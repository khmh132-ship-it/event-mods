package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.stock.Stockpile;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A trip to the warehouse to take things: walk to a chest that has some of what is wanted, open it, take it,
 * close it, and on to the next chest until everything is in the inventory or nothing more can be found.
 * Goods still in the buffer (arrived while unloaded) are picked up at the bell.
 */
class Fetching {
    private static final int OPEN_TICKS = 10;
    private static final int GIVE_UP_TICKS = 300;

    private final VillageWorker worker;
    private final Map<Item, Integer> wanted = new LinkedHashMap<>();
    private final Set<BlockPos> skip = new HashSet<>();
    private BlockPos target;
    private int timer;
    private int opened = -1;

    Fetching(VillageWorker worker) {
        this.worker = worker;
    }

    void start(Map<Item, Integer> items) {
        reset();
        items.forEach((item, n) -> {
            if (n > 0) {
                wanted.merge(item, n, Integer::sum);
            }
        });
    }

    boolean active() {
        return !wanted.isEmpty();
    }

    void reset() {
        if (target != null && opened >= 0 && worker.level() instanceof ServerLevel level) {
            ContainerLid.close(level, target);
        }
        wanted.clear();
        skip.clear();
        target = null;
        timer = 0;
        opened = -1;
    }

    /** Returns true once the trip is over (got everything, or nothing more to get). */
    boolean tick() {
        Village village = worker.village().orElse(null);
        if (wanted.isEmpty() || village == null || !(worker.level() instanceof ServerLevel level)) {
            reset();
            return true;
        }
        if (target == null) {
            target = pick(level, village);
            if (target == null) {
                reset();
                return true;
            }
            timer = 0;
            opened = -1;
        }
        timer++;
        boolean atBell = target.equals(village.center());
        if (worker.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > (atBell ? 9 : 6.25)) {
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
        if (atBell) {
            takeFromBuffer(village);
            skip.add(target);
            target = null;
            return false;
        }
        if (opened < 0) {
            ContainerLid.open(level, target);
            opened = timer;
            return false;
        }
        if (timer - opened < OPEN_TICKS) {
            return false;
        }
        takeFrom(level, target);
        ContainerLid.close(level, target);
        skip.add(target);
        target = null;
        return false;
    }

    private BlockPos pick(ServerLevel level, Village village) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        List<BlockPos> chests = Stockpile.containers(level, village);
        for (BlockPos p : chests) {
            if (skip.contains(p) || !(level.getBlockEntity(p) instanceof Container c) || !hasAny(c)) {
                continue;
            }
            double d = p.distSqr(worker.blockPosition());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best == null && !skip.contains(village.center())
                && wanted.keySet().stream().anyMatch(i -> village.storage().count(i) > 0)) {
            best = village.center();
        }
        return best;
    }

    private boolean hasAny(Container c) {
        for (Item item : wanted.keySet()) {
            if (c.countItem(item) > 0) {
                return true;
            }
        }
        return false;
    }

    private void takeFrom(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container c)) {
            return;
        }
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            Integer want = s.isEmpty() ? null : wanted.get(s.getItem());
            if (want == null) {
                continue;
            }
            int n = Math.min(want, s.getCount());
            ItemStack taken = s.split(n);
            ItemStack rest = worker.getInventory().addItem(taken);
            int got = n - rest.getCount();
            if (!rest.isEmpty()) {
                s.grow(rest.getCount()); // inventory full: put back
            }
            settle(taken.getItem(), got);
        }
        c.setChanged();
    }

    private void takeFromBuffer(Village village) {
        for (Map.Entry<Item, Integer> e : Map.copyOf(wanted).entrySet()) {
            int n = Math.min(e.getValue(), village.storage().count(e.getKey()));
            if (n <= 0) {
                continue;
            }
            ItemStack rest = worker.getInventory().addItem(new ItemStack(e.getKey(), n));
            int got = n - rest.getCount();
            village.storage().take(e.getKey(), got);
            settle(e.getKey(), got);
        }
    }

    private void settle(Item item, int got) {
        wanted.computeIfPresent(item, (k, v) -> v - got <= 0 ? null : v - got);
    }
}
