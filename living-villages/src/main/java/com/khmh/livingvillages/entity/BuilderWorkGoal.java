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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private static final int SUBSTITUTE_TICKS = 2400;

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
    private boolean unloading;

    public BuilderWorkGoal(VillageWorker worker) {
        this.worker = worker;
        this.fetching = new Fetching(worker);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (worker.job() != WorkerJob.BUILDER) {
            return false;
        }
        target = worker.village().flatMap(v -> v.buildings().stream().filter(b -> !b.isComplete()).findFirst())
                .orElse(null);
        unloading = target == null && worker.carried() > 0;
        return target != null || unloading;
    }

    @Override
    public boolean canContinueToUse() {
        if (worker.job() != WorkerJob.BUILDER) {
            return false;
        }
        return unloading ? worker.carried() > 0 : target != null && !target.isComplete();
    }

    @Override
    public void start() {
        lastNext = null;
        cooldown = 0;
        repath = 0;
        waitingSince = -1;
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
            worker.unloadTick();
            return;
        }
        if (fetching.active()) {
            fetching.tick();
            return;
        }
        long now = level.getGameTime();
        village.reportWorking(target.id(), now);
        worker.setStatus("building " + target.typeId() + " " + target.phase().name().toLowerCase() + " "
                + target.progress() + (waitingSince >= 0 ? " (waiting for materials)" : ""));

        Construction.Next next = Construction.next(level, target);
        BlockPos pos = next != null ? next.pos() : Construction.nextTarget(level, target);
        if (pos == null) {
            return;
        }
        BlockState substitute = null;
        boolean skip = false;
        Item need = next == null ? null : next.item();
        if (need != null && worker.getInventory().countItem(need) == 0) {
            if (startFetch(level, village, need)) {
                village.withdrawRequest(need, "build:" + target.id());
                return;
            }
            // Not in the warehouse: ask for it and wait; after a long wait make do with something similar.
            Map<Item, Integer> look = Construction.lookahead(level, target, LOOKAHEAD);
            village.request(need, look.getOrDefault(need, 1), "build:" + target.id(), now);
            if (waitingSince < 0) {
                waitingSince = now;
            }
            if (now - waitingSince < SUBSTITUTE_TICKS) {
                idleNear(pos);
                return;
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

    /** Fetches what the warehouse has of the next blocks' needs; false if it has none of {@code need}. */
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
            return stock.count(Items.OAK_PLANKS) > 0 ? Items.OAK_PLANKS : null;
        }
        if (kinds.contains(CostKey.Stone.INSTANCE)) {
            return worker.getInventory().countItem(Items.COBBLESTONE) > 0 || stock.count(Items.COBBLESTONE) > 0
                    ? Items.COBBLESTONE : null;
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
