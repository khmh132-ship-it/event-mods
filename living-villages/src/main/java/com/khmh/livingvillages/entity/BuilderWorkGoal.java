package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.building.Construction;
import com.khmh.livingvillages.building.CostKey;
import com.khmh.livingvillages.building.MaterialCost;
import com.khmh.livingvillages.config.LVConfig;
import com.khmh.livingvillages.stock.Stockpile;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The builder puts up buildings with his own hands and his own materials:
 * <ul>
 *     <li>before working he looks at what the next blocks need, fetches what the warehouse has and posts a
 *     request for what it has not;</li>
 *     <li>he walks within reach of the next block; if he cannot get closer he places it from where he stands,
 *     through whatever is in between;</li>
 *     <li>if a request stays unanswered for long he puts a similar block instead (planks for wooden things,
 *     cobblestone for stone ones) or leaves the spot empty, so a missing bookshelf never stalls a village;</li>
 *     <li>with nothing to build he takes his leftovers back to the warehouse.</li>
 * </ul>
 */
public class BuilderWorkGoal extends Goal {
    private static final double REACH = 4.5;
    private static final double FAR_REACH = 16.0;
    private static final int NO_PROGRESS_TICKS = 30;
    private static final int GIVE_UP_TICKS = 400;
    private static final int LOOKAHEAD = 64;
    /** How long he waits for a requested item before making do. */
    private static final int SUBSTITUTE_TICKS = 1200;

    private final VillageWorker worker;
    private final Fetching fetching;
    private Building target;
    private BlockPos lastNext;
    private double bestDist;
    private int noProgress;
    private int cooldown;
    private int repath;
    private int blockedBySelf;
    private long waitingSince = -1;
    /** Until when he leaves building to make something he needs (see {@link CrafterWorkGoal}). */
    private long yieldUntil;
    private boolean unloading;

    public BuilderWorkGoal(VillageWorker worker) {
        this.worker = worker;
        this.fetching = new Fetching(worker);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (worker.job() != WorkerJob.BUILDER || worker.level().getGameTime() < yieldUntil) {
            return false;
        }
        target = worker.village().flatMap(v -> v.buildings().stream().filter(b -> !b.isComplete()).findFirst())
                .orElse(null);
        unloading = target == null && worker.carried() > 0;
        return target != null || unloading;
    }

    @Override
    public boolean canContinueToUse() {
        if (worker.job() != WorkerJob.BUILDER || worker.level().getGameTime() < yieldUntil) {
            return false;
        }
        return unloading ? worker.carried() > 0 : target != null && !target.isComplete();
    }

    @Override
    public void start() {
        lastNext = null;
        cooldown = 0;
        repath = 0;
        // waitingSince survives a break (eating, making something): the wait for a stand-in keeps counting.
    }

    @Override
    public void stop() {
        target = null;
        unloading = false;
        fetching.reset();
        worker.resetUnloading();
        worker.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Village village = worker.village().orElse(null);
        if (village == null || !(worker.level() instanceof ServerLevel level)) {
            target = null;
            return;
        }
        if (unloading) {
            worker.setStatus("unloading");
            worker.unloadTick();
            return;
        }
        if (fetching.active()) {
            worker.setStatus("fetching materials");
            fetching.tick();
            return;
        }
        long now = level.getGameTime();
        village.reportWorking(target.id(), now);
        worker.setStatus("building " + target.typeId() + " " + target.phase().name().toLowerCase() + " "
                + target.progress() + (waitingSince >= 0 ? " (waiting for materials)" : ""));

        if (target.phase() == com.khmh.livingvillages.building.Building.Phase.PREPARE) {
            prepare(level, village, now);
            return;
        }
        Construction.Next next = Construction.next(level, target);
        BlockPos pos = next != null ? next.pos() : Construction.nextTarget(level, target);
        if (pos == null) {
            return;
        }
        BlockState substitute = null;
        boolean skip = false;
        Item need = next == null ? null : next.item();
        if (need != null && worker.getInventory().countItem(need) == 0 && new ItemStack(need).is(ItemTags.PLANKS)
                && handPlanks(level, village, need)) {
            return; // planks out of his own logs, in hand, like a player
        }
        if (need != null && worker.getInventory().countItem(need) == 0) {
            if (startFetch(level, village, need)) {
                village.withdrawRequest(need, "build:" + target.id());
                return;
            }
            // Oak this, oak that: when the village's wood is spruce or birch, the same thing in that wood will do.
            BlockState other = otherWood(level, village, next.state(), need);
            if (other != null) {
                Item otherItem = other.getBlock().asItem();
                village.withdrawRequest(need, "build:" + target.id()); // no point asking for oak any more
                if (worker.getInventory().countItem(otherItem) > 0) {
                    substitute = other;
                    need = otherItem;
                    waitingSince = -1;
                    // fall through to placing
                } else if (village.stock(level).count(otherItem) > 0) {
                    fetching.start(Map.of(otherItem, Math.min(16, village.stock(level).count(otherItem))));
                    return;
                } else {
                    village.request(otherItem, Construction.lookahead(level, target, LOOKAHEAD).getOrDefault(need, 1),
                            "build:" + target.id(), now);
                }
            }
            if (substitute != null) {
                // got a stand-in of another wood: no waiting
            } else {
            // Not in the warehouse: ask for it and wait; after a long wait make do with something similar.
            Map<Item, Integer> look = Construction.lookahead(level, target, LOOKAHEAD);
            if (other == null) {
                village.request(need, look.getOrDefault(need, 1), "build:" + target.id(), now);
            }
            if (waitingSince < 0) {
                waitingSince = now;
            }
            // Once a stand-in had to be used for this item on this site, the rest get one straight away.
            String needId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(need).toString();
            final Item needed = need;
            boolean givenUp = target.data().getList("standIns", net.minecraft.nbt.Tag.TAG_STRING).stream()
                    .anyMatch(t -> t.getAsString().equals(needId))
                    // Known not to be makeable by anyone here: no point standing about a full minute for it.
                    || village.requests().stream().anyMatch(r -> r.item() == needed
                            && r.requester().equals("build:" + target.id()) && r.unobtainableSince() >= 0);
            if (!givenUp && now - waitingSince < SUBSTITUTE_TICKS) {
                // Nobody to make it: he puts the trowel down and makes it himself, as a player would.
                if (village.professions().getOrDefault("apprentice", 0) == 0 && com.khmh.livingvillages.economy.CraftPlanner
                        .plan(level, village.stock(level).totals(), other != null ? other.getBlock().asItem() : need, 1)
                        .isPresent()) {
                    yieldUntil = now + 100;
                    return;
                }
                idleNear(pos);
                return;
            }
            if (!givenUp) {
                net.minecraft.nbt.ListTag standIns = target.data().getList("standIns", net.minecraft.nbt.Tag.TAG_STRING);
                standIns.add(net.minecraft.nbt.StringTag.valueOf(needId));
                target.data().put("standIns", standIns);
                village.withdrawRequest(need, "build:" + target.id());
                village.markDirty();
            }
            Item alt = substituteFor(next.state(), village.stock(level));
            if (alt != null && worker.getInventory().countItem(alt) == 0) {
                fetching.start(Map.of(alt, 16));
                return;
            }
            if (alt != null && alt instanceof BlockItem bi) {
                substitute = bi.getBlock().defaultBlockState();
                need = alt;
            } else {
                skip = true;
                need = null;
            }
            }
        } else {
            waitingSince = -1;
        }

        if (!pos.equals(lastNext)) {
            lastNext = pos;
            bestDist = Double.MAX_VALUE;
            noProgress = 0;
        }
        double dist = Math.sqrt(worker.distanceToSqr(Vec3.atCenterOf(pos)));
        if (dist < bestDist - 0.3) {
            bestDist = dist;
            noProgress = 0;
        } else {
            noProgress++;
        }
        boolean closeEnough = skip || dist <= REACH
                || noProgress > NO_PROGRESS_TICKS && worker.getNavigation().isDone() && dist <= FAR_REACH
                || noProgress > GIVE_UP_TICKS;
        if (!closeEnough) {
            if (--repath <= 0) {
                Path path = worker.getNavigation().createPath(pos, 1);
                if (path != null) {
                    worker.getNavigation().moveTo(path, 0.6);
                }
                repath = 20;
            }
            return;
        }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (--cooldown > 0) {
            return;
        }
        cooldown = (int) Math.max(2, LVConfig.BUILD_INTERVAL.get() / 2 / worker.workSpeed());
        if (!skip && worker.getBoundingBox().intersects(new AABB(pos))) {
            if (++blockedBySelf < 40) {
                stepOff();
                return;
            }
            worker.setPos(worker.getX(), pos.getY() + 1, worker.getZ()); // climbs onto it
        }
        blockedBySelf = 0;
        if (need != null) {
            worker.getInventory().removeItemType(need, 1);
        }
        Construction.placeByBuilder(level, village, target, now, substitute, skip);
        waitingSince = -1;
        if (!skip) {
            worker.swing(InteractionHand.MAIN_HAND);
        }
    }

    private BlockPos prepBlock;
    private int prepTicks;
    private int prepWalk;

    /**
     * Levelling the site by hand: each block in the way is broken like a player breaks it (time by hardness and
     * the best tool he carries, cracks showing, the drops into his pockets); dips are filled with the dirt he dug.
     */
    private void prepare(ServerLevel level, Village village, long now) {
        if (worker.inventoryFull()) {
            unloading = true; // pockets full of dirt and stone: to the chests first
            return;
        }
        Construction.Prep work = Construction.nextPrep(level, target);
        if (work == null) {
            return; // level: building starts next tick
        }
        BlockPos pos = work.pos();
        if (!pos.equals(prepBlock)) {
            if (prepBlock != null) {
                level.destroyBlockProgress(worker.getId(), prepBlock, -1);
            }
            prepBlock = pos;
            prepTicks = 0;
            prepWalk = 0;
        }
        double dist = Math.sqrt(worker.distanceToSqr(Vec3.atCenterOf(pos)));
        double dx = worker.getX() - (pos.getX() + 0.5), dz = worker.getZ() - (pos.getZ() + 0.5);
        BlockState at = level.getBlockState(pos);
        // A tree on the site is felled from its foot, as the lumberjack does: the top of a tall spruce is out of
        // anybody's reach. And whatever he cannot get near for a full minute he deals with from where he stands,
        // rather than a village waiting on one block for ever.
        boolean tree = (at.is(BlockTags.LOGS) || at.is(BlockTags.LEAVES)) && dx * dx + dz * dz <= REACH * REACH
                && worker.getY() <= pos.getY();
        if (dist > REACH && !tree && !(prepWalk > 200 && dist <= 8) && prepWalk <= 1200) {
            prepWalk++;
            if (--repath <= 0) {
                worker.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.6);
                repath = 20;
            }
            return;
        }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        var inv = worker.getInventory();
        if (work.fill()) {
            if (worker.getBoundingBox().intersects(new AABB(pos))) {
                stepOff();
                return;
            }
            Item soil = inv.countItem(Items.DIRT) > 0 ? Items.DIRT : inv.countItem(Items.COBBLESTONE) > 0 ? Items.COBBLESTONE : null;
            if (soil == null && village.stock(level).count(Items.DIRT) >= 1) {
                fetching.start(Map.of(Items.DIRT, Math.min(32, village.stock(level).count(Items.DIRT))));
                return;
            }
            if (soil != null) {
                inv.removeItemType(soil, 1);
            }
            level.setBlock(pos, (soil == Items.COBBLESTONE ? Blocks.COBBLESTONE : Blocks.DIRT).defaultBlockState(),
                    Block.UPDATE_ALL);
            worker.swing(InteractionHand.MAIN_HAND);
            return;
        }
        BlockState state = level.getBlockState(pos);
        ItemStack tool = bestToolFor(state);
        float hardness = state.getDestroySpeed(level, pos);
        float speed = Math.max(1.0F, tool.getDestroySpeed(state));
        boolean proper = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        int need = Math.max(2, Math.round(hardness * (proper ? 30 : 100) / speed / (float) worker.workSpeed()));
        prepTicks++;
        level.destroyBlockProgress(worker.getId(), pos, Math.min(9, prepTicks * 10 / need));
        if (prepTicks % 5 == 0) {
            worker.swing(InteractionHand.MAIN_HAND);
        }
        if (prepTicks < need) {
            return;
        }
        level.destroyBlockProgress(worker.getId(), pos, -1);
        if (proper) {
            for (ItemStack drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), worker, tool)) {
                worker.carry(drop.getItem(), drop.getCount());
            }
        }
        level.destroyBlock(pos, false, worker); // the cracking sound and dust, no item on the ground
        if (!tool.isEmpty() && tool.isDamageableItem()) {
            tool.hurtAndBreak(1, worker, w -> { });
        }
        prepBlock = null;
        village.markDirty();
    }

    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"};

    /**
     * The same block in the wood the village actually has (spruce stairs for oak stairs, say), turned and set the
     * same way; null if the block is not wooden or the village has no other wood it could be made of.
     */
    @Nullable
    private BlockState otherWood(ServerLevel level, Village village, BlockState wanted, Item need) {
        String path = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(need).getPath();
        String from = null;
        for (String w : WOODS) {
            // oak_stairs, stripped_oak_log... (dark_oak beats oak when both fit)
            if ((path.startsWith(w + "_") || path.contains("_" + w + "_")) && (from == null || w.length() > from.length())) {
                from = w;
            }
        }
        if (from == null) {
            return null;
        }
        var stock = village.stock(level);
        String best = null;
        int bestHave = 0;
        for (String w : WOODS) {
            if (w.equals(from)) {
                continue;
            }
            Item log = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(w + "_log"));
            Item planks = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(w + "_planks"));
            int have = (log == null ? 0 : stock.count(log) * 4 + worker.getInventory().countItem(log) * 4)
                    + (planks == null ? 0 : stock.count(planks) + worker.getInventory().countItem(planks));
            if (have > bestHave) {
                bestHave = have;
                best = w;
            }
        }
        if (best == null) {
            return null;
        }
        Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                new net.minecraft.resources.ResourceLocation(path.startsWith(from + "_")
                        ? best + path.substring(from.length()) : path.replace("_" + from + "_", "_" + best + "_")));
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        BlockState out = block.defaultBlockState();
        for (var prop : wanted.getProperties()) {
            out = copy(out, wanted, prop);
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState to, BlockState from,
                                                             net.minecraft.world.level.block.state.properties.Property<T> prop) {
        return to.hasProperty(prop) ? to.setValue(prop, from.getValue(prop)) : to;
    }

    /** The tool in his pockets (or hand) that breaks this block fastest; empty for bare hands. */
    private ItemStack bestToolFor(BlockState state) {
        ItemStack best = worker.getMainHandItem();
        float bestSpeed = best.getDestroySpeed(state);
        var inv = worker.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getDestroySpeed(state) > bestSpeed) {
                best = s;
                bestSpeed = s.getDestroySpeed(state);
            }
        }
        return best;
    }

    /** Fetches what the warehouse has of the next blocks' needs; false if it has none of {@code need}. */
    /**
     * Saws planks from a log he carries (one log, four planks); with no logs on him but logs in store and no
     * planks there, he goes for a load of logs. True if he did something about it.
     */
    private boolean handPlanks(ServerLevel level, Village village, Item planks) {
        var inv = worker.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ItemTags.LOGS)) {
                s.shrink(1);
                inv.setChanged();
                worker.carry(planks, 4);
                worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                return true;
            }
        }
        Stockpile stock = village.stock(level);
        if (stock.count(planks) == 0) {
            for (var e : stock.totals().entrySet()) {
                if (e.getValue() > 0 && new ItemStack(e.getKey()).is(ItemTags.LOGS)) {
                    fetching.start(Map.of(e.getKey(), Math.min(16, e.getValue())));
                    return true;
                }
            }
        }
        return false;
    }

    private boolean startFetch(ServerLevel level, Village village, Item need) {
        Stockpile stock = village.stock(level);
        if (stock.count(need) <= 0) {
            return false;
        }
        Map<Item, Integer> take = new LinkedHashMap<>();
        int stacks = 0;
        for (Map.Entry<Item, Integer> e : Construction.lookahead(level, target, LOOKAHEAD).entrySet()) {
            int missing = e.getValue() - worker.getInventory().countItem(e.getKey());
            int have = stock.count(e.getKey());
            int n = Math.min(missing, have);
            if (n > 0 && stacks < 16) {
                take.put(e.getKey(), n);
                stacks += (n + e.getKey().getMaxStackSize() - 1) / e.getKey().getMaxStackSize();
            }
        }
        if (take.isEmpty()) {
            return false;
        }
        fetching.start(take);
        return true;
    }

    /** Planks for wooden things, cobblestone for stone ones; null if there is no sensible stand-in. */
    @Nullable
    private Item substituteFor(BlockState state, Stockpile stock) {
        Set<CostKey> kinds = MaterialCost.of(state).keySet();
        if (kinds.contains(CostKey.Wood.INSTANCE)) {
            for (int i = 0; i < worker.getInventory().getContainerSize(); i++) {
                ItemStack s = worker.getInventory().getItem(i);
                if (s.is(ItemTags.PLANKS)) {
                    return s.getItem();
                }
            }
            return stock.totals().entrySet().stream()
                    .filter(e -> e.getValue() > 0 && new ItemStack(e.getKey()).is(ItemTags.PLANKS))
                    .map(java.util.Map.Entry::getKey).findFirst().orElse(null); // any wood's planks
        }
        if (kinds.contains(CostKey.Stone.INSTANCE)) {
            return worker.getInventory().countItem(Items.COBBLESTONE) > 0 || stock.count(Items.COBBLESTONE) > 0
                    ? Items.COBBLESTONE : null;
        }
        // A wall block nobody can make (terracotta, wool...): any solid block rather than a hole in the wall.
        if (state.isSolidRender(worker.level(), worker.blockPosition())) {
            for (Item solid : new Item[]{Items.COBBLESTONE, Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
                    Items.DIRT}) {
                if (worker.getInventory().countItem(solid) > 0 || stock.count(solid) > 0) {
                    return solid;
                }
            }
        }
        return null;
    }

    private void idleNear(BlockPos pos) {
        if (worker.distanceToSqr(Vec3.atCenterOf(pos)) > 64 && --repath <= 0) {
            worker.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.5);
            repath = 40;
        }
    }

    /** The next block goes where the builder stands: he takes a step back first. */
    private void stepOff() {
        BlockPos away = worker.blockPosition().relative(worker.getDirection().getOpposite(), 2);
        worker.getNavigation().moveTo(away.getX() + 0.5, worker.getY(), away.getZ() + 0.5, 0.6);
    }
}
