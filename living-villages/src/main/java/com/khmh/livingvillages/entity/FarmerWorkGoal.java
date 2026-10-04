package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The farmer works every field of the village: harvests ripe crops (getting the real drops), plants seeds back
 * into empty farmland, and takes the harvest to the warehouse, keeping enough seeds in his pockets for the next
 * round. With no seeds left he fetches some from the warehouse.
 */
public class FarmerWorkGoal extends Goal {
    private static final int LOAD = 48;
    private static final int SCAN_EVERY = 100;
    private static final Map<Item, Integer> KEEP = Map.of(Items.WHEAT_SEEDS, 16, Items.CARROT, 16,
            Items.POTATO, 16, Items.BEETROOT_SEEDS, 16);

    private enum State { WORK, RETURN }

    private final VillageWorker worker;
    private final Fetching fetching;
    private State state = State.WORK;
    private List<BlockPos> todo = List.of();
    private BlockPos target;
    private int scan;
    private int timer;

    public FarmerWorkGoal(VillageWorker worker) {
        this.worker = worker;
        this.fetching = new Fetching(worker);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return worker.job() == WorkerJob.FARMER && worker.village().isPresent();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        fetching.reset();
        worker.resetUnloading();
        worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        Village village = worker.village().orElse(null);
        if (village == null || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        timer++;
        worker.setStatus(state.name().toLowerCase() + " t" + timer);
        if (state == State.RETURN) {
            if (worker.unloadTick(KEEP)) {
                state = State.WORK;
            }
            return;
        }
        if (fetching.active()) {
            fetching.tick();
            return;
        }
        if (forage != null) {
            forageTick(level);
            return;
        }
        if (--scan <= 0) {
            todo = findWork(level, village);
            scan = SCAN_EVERY;
        }
        if (target == null || !needsWork(level, target)) {
            target = todo.stream().filter(p -> needsWork(level, p))
                    .min(Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition()))).orElse(null);
            timer = 0;
        }
        if (target == null) {
            // Fields growing and nothing to do on them: berries off the bushes about the village meanwhile.
            if (berryTick(level, village)) {
                return;
            }
            if (harvestCarried() > 0) {
                state = State.RETURN; // fields done: take the harvest in
            } else {
                // Everything sown and growing, no berries about: nothing to do, the village may find him work.
                worker.setStatus("none (fields growing)");
            }
            return;
        }
        if (harvestCarried() >= LOAD || worker.inventoryFull()) {
            state = State.RETURN;
            return;
        }
        double d2 = worker.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        // Close by but no way onto it (a row up a step, across the water channel): reached over, as a player would.
        boolean reachOver = d2 <= 16.0 && timer > 60;
        if (d2 > 5.0 && !reachOver) {
            if (timer > 300) {
                todo = todo.stream().filter(p -> !p.equals(target)).toList();
                target = null;
            } else if (timer % 20 == 1) {
                worker.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.6);
            }
            return;
        }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        if (timer % 8 != 0) {
            return;
        }
        BlockState crop = level.getBlockState(target);
        if (crop.getBlock() instanceof CropBlock c && c.isMaxAge(crop)) {
            for (ItemStack drop : Block.getDrops(crop, level, target, null, worker, worker.getMainHandItem())) {
                worker.carry(drop.getItem(), drop.getCount());
            }
            level.destroyBlock(target, false, worker);
            worker.swing(InteractionHand.MAIN_HAND);
            replant(level, village, target, seedFor(crop));
        } else if (crop.isAir()) {
            replant(level, village, target, null);
        }
    }

    private BlockPos berries;
    private int berryTimer;
    private int berryLook;

    private boolean berryTick(ServerLevel level, Village village) {
        if (berries == null || !ripe(level.getBlockState(berries)) || ++berryTimer > 400) {
            berries = null;
            berryTimer = 0;
            if (--berryLook > 0 || harvestCarried() >= LOAD) {
                return false;
            }
            berryLook = 200;
            BlockPos c = village.center();
            int r = Math.min(40, village.radius());
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -6, -r), c.offset(r, 6, r))) {
                if (level.hasChunkAt(p) && ripe(level.getBlockState(p))
                        && (berries == null || p.distSqr(worker.blockPosition()) < berries.distSqr(worker.blockPosition()))) {
                    berries = p.immutable();
                }
            }
            if (berries == null) {
                return false;
            }
        }
        worker.setStatus("picking berries");
        if (worker.distanceToSqr(berries.getX() + 0.5, berries.getY(), berries.getZ() + 0.5) > 5.0) {
            if (berryTimer % 20 == 1) {
                worker.getNavigation().moveTo(berries.getX() + 0.5, berries.getY(), berries.getZ() + 0.5, 0.6);
            }
            return true;
        }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(berries.getX() + 0.5, berries.getY() + 0.3, berries.getZ() + 0.5);
        if (berryTimer % 10 == 0) {
            BlockState bush = level.getBlockState(berries);
            int age = bush.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE);
            // As for a player picking them: one or two from a half-grown bush, two or three from a full one.
            worker.carry(Items.SWEET_BERRIES, 1 + level.random.nextInt(2) + (age == 3 ? 1 : 0));
            level.setBlock(berries, bush.setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 1), Block.UPDATE_CLIENTS);
            level.playSound(null, berries, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);
            worker.swing(InteractionHand.MAIN_HAND);
            berries = null;
            berryLook = 0; // straight on to the next bush
        }
        return true;
    }

    private static boolean ripe(BlockState s) {
        return s.is(Blocks.SWEET_BERRY_BUSH) && s.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) >= 2;
    }

    private BlockPos forage;
    private int forageTimer;

    /** Walks to a tuft of grass and pulls it up (seeds drop now and then, as for a player). */
    private void forageTick(ServerLevel level) {
        worker.setStatus("pulling grass for seeds");
        if (++forageTimer > 400 || !isGrass(level.getBlockState(forage))) {
            forage = KEEP.keySet().stream().anyMatch(s -> worker.getInventory().countItem(s) >= 4) || forageTimer > 400
                    ? null : grassNear(level);
            forageTimer = 0;
            return;
        }
        if (worker.distanceToSqr(forage.getX() + 0.5, forage.getY(), forage.getZ() + 0.5) > 6.0) {
            if (forageTimer % 20 == 1) {
                worker.getNavigation().moveTo(forage.getX() + 0.5, forage.getY(), forage.getZ() + 0.5, 0.6);
            }
            return;
        }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(forage.getX() + 0.5, forage.getY() + 0.3, forage.getZ() + 0.5);
        if (forageTimer % 6 != 0) {
            return;
        }
        BlockState grass = level.getBlockState(forage);
        for (ItemStack drop : Block.getDrops(grass, level, forage, null, worker, ItemStack.EMPTY)) {
            worker.carry(drop.getItem(), drop.getCount());
        }
        level.destroyBlock(forage, false, worker);
        worker.swing(InteractionHand.MAIN_HAND);
        forage = KEEP.keySet().stream().anyMatch(s -> worker.getInventory().countItem(s) >= 4) ? null : grassNear(level);
        forageTimer = 0;
    }

    private static boolean isGrass(BlockState s) {
        return s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN);
    }

    private BlockPos grassNear(ServerLevel level) {
        BlockPos at = worker.blockPosition();
        BlockPos best = null;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-16, -4, -16), at.offset(16, 4, 16))) {
            if (level.hasChunkAt(p) && isGrass(level.getBlockState(p))
                    && (best == null || p.distSqr(at) < best.distSqr(at))) {
                best = p.immutable();
            }
        }
        return best;
    }

    private void replant(ServerLevel level, Village village, BlockPos pos, Item preferred) {
        Item seed = preferred != null && worker.getInventory().countItem(preferred) > 0 ? preferred
                : KEEP.keySet().stream().filter(s -> worker.getInventory().countItem(s) > 0).findFirst().orElse(null);
        if (seed == null) {
            Map<Item, Integer> stock = village.stock(level).totals();
            Map<Item, Integer> want = KEEP.keySet().stream().filter(s -> stock.getOrDefault(s, 0) > 0)
                    .collect(Collectors.toMap(s -> s, s -> Math.min(16, stock.get(s)), (a, b) -> a, LinkedHashMap::new));
            if (!want.isEmpty()) {
                fetching.start(want);
            } else {
                forage = grassNear(level); // none in store either: off to pull up grass for seeds
            }
            target = null;
            return;
        }
        Block plant = seed == Items.CARROT ? Blocks.CARROTS : seed == Items.POTATO ? Blocks.POTATOES
                : seed == Items.BEETROOT_SEEDS ? Blocks.BEETROOTS : Blocks.WHEAT;
        worker.getInventory().removeItemType(seed, 1);
        level.setBlock(pos, plant.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        target = null;
    }

    private static Item seedFor(BlockState crop) {
        if (crop.is(Blocks.CARROTS)) {
            return Items.CARROT;
        }
        if (crop.is(Blocks.POTATOES)) {
            return Items.POTATO;
        }
        if (crop.is(Blocks.BEETROOTS)) {
            return Items.BEETROOT_SEEDS;
        }
        return Items.WHEAT_SEEDS;
    }

    /** Whether any farm of the village has ripe crops or bare farmland right now (cheap: farm sites only). */
    public static boolean hasWork(ServerLevel level, Village village) {
        for (com.khmh.livingvillages.building.Building b : village.buildings()) {
            var type = com.khmh.livingvillages.building.BuildingTypes.get(b.typeId());
            if (type == null || !"farm".equals(type.group()) || !b.isComplete()) {
                continue;
            }
            var box = b.box();
            if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
                continue;
            }
            if (BlockPos.betweenClosedStream(box).anyMatch(p -> needsWork(level, p))) {
                return true;
            }
        }
        return false;
    }

    /** Ripe crops and empty farmland within the village. */
    private static List<BlockPos> findWork(ServerLevel level, Village village) {
        // The village's own fields only: sweeping the whole village block by block grows costly as it grows.
        List<BlockPos> out = new java.util.ArrayList<>();
        for (com.khmh.livingvillages.building.Building b : village.buildings()) {
            var type = com.khmh.livingvillages.building.BuildingTypes.get(b.typeId());
            if (type == null || !"farm".equals(type.group()) || !b.isComplete()) {
                continue;
            }
            var box = b.box();
            if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
                continue;
            }
            for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (level.getBlockState(p).is(Blocks.FARMLAND) && needsWork(level, p.above())) {
                    out.add(p.above().immutable());
                }
            }
        }
        if (village.countGroup("farm") > 0) {
            return out;
        }
        // An old village whose fields are not buildings of ours: look about for farmland as before.
        BlockPos c = village.center();
        int r = Math.min(48, village.radius());
        return BlockPos.betweenClosedStream(c.getX() - r, c.getY() - 8, c.getZ() - r, c.getX() + r, c.getY() + 8,
                        c.getZ() + r)
                .filter(p -> level.hasChunkAt(p) && level.getBlockState(p).is(Blocks.FARMLAND))
                .map(p -> p.above().immutable())
                .filter(p -> needsWork(level, p))
                .collect(Collectors.toList());
    }

    private static boolean needsWork(ServerLevel level, BlockPos p) {
        if (!level.getBlockState(p.below()).is(Blocks.FARMLAND)) {
            return false;
        }
        BlockState s = level.getBlockState(p);
        return s.isAir() || s.getBlock() instanceof CropBlock c && c.isMaxAge(s);
    }

    private int harvestCarried() {
        int n = 0;
        for (int i = 0; i < worker.getInventory().getContainerSize(); i++) {
            ItemStack s = worker.getInventory().getItem(i);
            n += s.getCount() - Math.min(s.getCount(), KEEP.getOrDefault(s.getItem(), 0));
        }
        return n;
    }
}
