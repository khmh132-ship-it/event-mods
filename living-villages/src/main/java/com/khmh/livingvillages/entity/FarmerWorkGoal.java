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
            if (harvestCarried() > 0) {
                state = State.RETURN; // fields done: take the harvest in
            }
            return;
        }
        if (harvestCarried() >= LOAD || worker.inventoryFull()) {
            state = State.RETURN;
            return;
        }
        if (worker.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > 5.0) {
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

    private void replant(ServerLevel level, Village village, BlockPos pos, Item preferred) {
        Item seed = preferred != null && worker.getInventory().countItem(preferred) > 0 ? preferred
                : KEEP.keySet().stream().filter(s -> worker.getInventory().countItem(s) > 0).findFirst().orElse(null);
        if (seed == null) {
            Map<Item, Integer> stock = village.stock(level).totals();
            Map<Item, Integer> want = KEEP.keySet().stream().filter(s -> stock.getOrDefault(s, 0) > 0)
                    .collect(Collectors.toMap(s -> s, s -> Math.min(16, stock.get(s)), (a, b) -> a, LinkedHashMap::new));
            if (!want.isEmpty()) {
                fetching.start(want);
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

    /** Ripe crops and empty farmland within the village. */
    private static List<BlockPos> findWork(ServerLevel level, Village village) {
        BlockPos c = village.center();
        int r = village.radius();
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
