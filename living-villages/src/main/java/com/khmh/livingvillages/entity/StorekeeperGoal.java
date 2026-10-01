package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.stock.Stockpile;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The storekeeper keeps the warehouse in order: every kind of item goes to the chest that already holds the
 * most of it, and half-empty stacks are merged. Takes things out of one chest, walks over, puts them in the
 * other, like anybody would.
 */
public class StorekeeperGoal extends Goal {
    private final VillageWorker worker;
    private BlockPos from;
    private BlockPos to;
    private Item item;
    private int phase;
    private int timer;
    private int cooldown;

    public StorekeeperGoal(VillageWorker worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (worker.job() != WorkerJob.STOREKEEPER || --cooldown > 0) {
            return false;
        }
        cooldown = 100;
        Village v = worker.village().orElse(null);
        if (v == null || !(worker.level() instanceof ServerLevel level)) {
            return false;
        }
        if (worker.carried() > 0) {
            from = null;
            return true;
        }
        return plan(level, v);
    }

    @Override
    public boolean canContinueToUse() {
        return worker.job() == WorkerJob.STOREKEEPER && timer < 1200 && (phase < 2 || worker.carried() > 0);
    }

    @Override
    public void start() {
        phase = from == null ? 2 : 0;
        timer = 0;
    }

    @Override
    public void stop() {
        worker.resetUnloading();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Finds an item kind spread over several chests and picks the move that tidies it best. */
    private boolean plan(ServerLevel level, Village v) {
        List<BlockPos> chests = Stockpile.containers(level, v);
        Map<Item, Map<BlockPos, Integer>> where = new HashMap<>();
        for (BlockPos p : chests) {
            if (level.getBlockEntity(p) instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack s = c.getItem(i);
                    if (!s.isEmpty()) {
                        where.computeIfAbsent(s.getItem(), k -> new HashMap<>()).merge(p, s.getCount(), Integer::sum);
                    }
                }
            }
        }
        for (Map.Entry<Item, Map<BlockPos, Integer>> e : where.entrySet()) {
            if (e.getValue().size() < 2) {
                continue;
            }
            BlockPos home = e.getValue().entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
            BlockPos stray = e.getValue().entrySet().stream().min(Map.Entry.comparingByValue()).get().getKey();
            if (!home.equals(stray) && hasRoom(level, home, e.getKey())) {
                item = e.getKey();
                from = stray;
                to = home;
                return true;
            }
        }
        return false;
    }

    private static boolean hasRoom(ServerLevel level, BlockPos p, Item item) {
        if (!(level.getBlockEntity(p) instanceof Container c)) {
            return false;
        }
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty() || s.is(item) && s.getCount() < s.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        if (!(worker.level() instanceof ServerLevel level)) {
            return;
        }
        timer++;
        if (phase == 2) {
            worker.setStatus("putting things away");
            worker.unloadTick();
            return;
        }
        BlockPos at = phase == 0 ? from : to;
        worker.setStatus("sorting the warehouse");
        if (worker.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) > 6.25) {
            if (timer % 20 == 1) {
                worker.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.6);
            }
            return;
        }
        worker.getNavigation().stop();
        if (!(level.getBlockEntity(at) instanceof Container c)) {
            phase = 2;
            return;
        }
        ContainerLid.open(level, at);
        if (phase == 0) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.is(item)) {
                    c.setItem(i, worker.getInventory().addItem(s.copy()));
                }
            }
            phase = 1;
        } else {
            for (int i = 0; i < worker.getInventory().getContainerSize(); i++) {
                ItemStack s = worker.getInventory().getItem(i);
                if (!s.isEmpty()) {
                    worker.getInventory().setItem(i, Stockpile.insertInto(c, s));
                }
            }
            phase = 2; // anything that did not fit goes wherever there is room
        }
        c.setChanged();
        ContainerLid.close(level, at);
    }
}
