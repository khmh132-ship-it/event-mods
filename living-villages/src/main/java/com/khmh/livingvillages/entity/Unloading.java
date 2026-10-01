package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.stock.Stockpile;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A worker's trip to the warehouse: walk to the nearest chest or barrel with room, open it, put the load in,
 * close it, and go on to the next one if it filled up. With no chest anywhere (a brand-new village) the load
 * is left at the bell, which counts as the village store until the first warehouse is built.
 */
class Unloading {
    private static final int OPEN_TICKS = 10;
    private static final int GIVE_UP_TICKS = 300;

    private final VillageWorker worker;
    private final Set<BlockPos> skip = new HashSet<>();
    private BlockPos target;
    private int timer;
    private int opened = -1;

    Unloading(VillageWorker worker) {
        this.worker = worker;
    }

    void reset() {
        if (target != null && opened >= 0 && worker.level() instanceof ServerLevel level) {
            close(level, target);
        }
        target = null;
        timer = 0;
        opened = -1;
        skip.clear();
    }

    boolean tick() {
        if (worker.carried() == 0) {
            reset();
            return true;
        }
        Village village = worker.village().orElse(null);
        if (village == null || !(worker.level() instanceof ServerLevel level)) {
            return true;
        }
        if (target == null) {
            target = pick(level, village);
            timer = 0;
            opened = -1;
        }
        timer++;
        double reach = target.equals(village.center()) ? 9 : 6.25;
        if (worker.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > reach) {
            if (timer > GIVE_UP_TICKS) {
                skip.add(target);
                target = null;
            } else if (timer % 20 == 1) {
                worker.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.6);
            }
            return false;
        }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (target.equals(village.center())) {
            putInBuffer(village);
            reset();
            return true;
        }
        if (opened < 0) {
            open(level, target);
            opened = timer;
            return false;
        }
        if (timer - opened < OPEN_TICKS) {
            return false;
        }
        boolean emptied = putInto(level, target);
        close(level, target);
        if (!emptied) {
            skip.add(target); // full: on to the next chest
        }
        target = null;
        return emptied && worker.carried() == 0;
    }

    private BlockPos pick(ServerLevel level, Village village) {
        List<BlockPos> chests = Stockpile.containers(level, village);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : chests) {
            if (skip.contains(p) || !hasRoom(level, p)) {
                continue;
            }
            double d = p.distSqr(worker.blockPosition());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best != null ? best : village.center();
    }

    private static boolean hasRoom(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container c)) {
            return false;
        }
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private boolean putInto(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container c)) {
            return false;
        }
        SimpleContainer inv = worker.getInventory();
        boolean all = true;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            ItemStack rest = Stockpile.insertInto(c, s);
            inv.setItem(i, rest);
            all &= rest.isEmpty();
        }
        return all;
    }

    private void putInBuffer(Village village) {
        SimpleContainer inv = worker.getInventory();
        for (ItemStack s : inv.removeAllItems()) {
            village.storage().add(s.getItem(), s.getCount());
        }
    }

    private static void open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BarrelBlock) {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        } else {
            level.blockEvent(pos, state.getBlock(), 1, 1);
            level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
    }

    private static void close(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BarrelBlock) {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
        } else {
            level.blockEvent(pos, state.getBlock(), 1, 0);
            level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
    }
}
