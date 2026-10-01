package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.stock.Stockpile;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

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
    /** Items the worker keeps for his own work (a farmer's seeds), item -> how many. */
    private java.util.Map<net.minecraft.world.item.Item, Integer> keep = java.util.Map.of();

    Unloading(VillageWorker worker) {
        this.worker = worker;
    }

    void reset() {
        if (target != null && opened >= 0 && worker.level() instanceof ServerLevel level) {
            ContainerLid.close(level, target);
        }
        target = null;
        timer = 0;
        opened = -1;
        skip.clear();
    }

    void keep(java.util.Map<net.minecraft.world.item.Item, Integer> keep) {
        this.keep = keep;
    }

    private int keptCount() {
        int n = 0;
        for (var e : keep.entrySet()) {
            n += Math.min(e.getValue(), worker.getInventory().countItem(e.getKey()));
        }
        return n;
    }

    boolean tick() {
        if (worker.carried() - keptCount() <= 0) {
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
            ContainerLid.open(level, target);
            opened = timer;
            return false;
        }
        if (timer - opened < OPEN_TICKS) {
            return false;
        }
        boolean emptied = putInto(level, target);
        ContainerLid.close(level, target);
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
        java.util.Map<net.minecraft.world.item.Item, Integer> keepLeft = new java.util.HashMap<>(keep);
        boolean all = true;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            int kept = Math.min(s.getCount(), keepLeft.getOrDefault(s.getItem(), 0));
            if (kept > 0) {
                keepLeft.put(s.getItem(), keepLeft.get(s.getItem()) - kept);
                if (kept == s.getCount()) {
                    continue;
                }
            }
            ItemStack give = s.copyWithCount(s.getCount() - kept);
            ItemStack rest = Stockpile.insertInto(c, give);
            s.setCount(kept + rest.getCount());
            all &= rest.isEmpty();
        }
        inv.setChanged();
        return all;
    }

    private void putInBuffer(Village village) {
        SimpleContainer inv = worker.getInventory();
        java.util.Map<net.minecraft.world.item.Item, Integer> keepLeft = new java.util.HashMap<>(keep);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            int kept = Math.min(s.getCount(), keepLeft.getOrDefault(s.getItem(), 0));
            if (s.isEmpty() || kept == s.getCount()) {
                keepLeft.computeIfPresent(s.getItem(), (k, v) -> v - kept);
                continue;
            }
            keepLeft.computeIfPresent(s.getItem(), (k, v) -> v - kept);
            village.storage().add(s.getItem(), s.getCount() - kept);
            s.setCount(kept);
        }
        inv.setChanged();
    }
}
