package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.stock.Stockpile;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Carriers keep goods flowing to the warehouse: they empty the chests of the other buildings (a hut's chest,
 * the mine's chest...) and carry the load over. Loose items they come across are picked up on the way.
 */
public class CarrierGoal extends Goal {
    private final VillageWorker worker;
    private BlockPos source;
    private int timer;
    private int opened = -1;
    private boolean unloading;
    private int cooldown;

    public CarrierGoal(VillageWorker worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (worker.job() != WorkerJob.CARRIER || --cooldown > 0) {
            return false;
        }
        cooldown = 40;
        return worker.village().isPresent();
    }

    @Override
    public boolean canContinueToUse() {
        return worker.job() == WorkerJob.CARRIER && (source != null || unloading || worker.carried() > 0);
    }

    @Override
    public void start() {
        Village v = worker.village().orElse(null);
        if (v == null || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        unloading = worker.carried() > 0;
        if (!unloading) {
            List<BlockPos> outlying = Stockpile.outlying(level, v);
            source = outlying.stream().min(Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition()))).orElse(null);
        }
        timer = 0;
        opened = -1;
    }

    @Override
    public void stop() {
        if (source != null && opened >= 0 && worker.level() instanceof ServerLevel level) {
            ContainerLid.close(level, source);
        }
        source = null;
        unloading = false;
        worker.resetUnloading();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(worker.level() instanceof ServerLevel level)) {
            return;
        }
        timer++;
        if (unloading || source == null) {
            worker.setStatus("carrying to the warehouse");
            if (worker.unloadTick()) {
                unloading = false;
            }
            return;
        }
        worker.setStatus("emptying a chest at " + source.toShortString());
        if (worker.distanceToSqr(source.getX() + 0.5, source.getY() + 0.5, source.getZ() + 0.5) > 6.25) {
            if (timer > 400) {
                source = null;
            } else if (timer % 20 == 1) {
                worker.getNavigation().moveTo(source.getX() + 0.5, source.getY(), source.getZ() + 0.5, 0.6);
            }
            return;
        }
        worker.getNavigation().stop();
        if (opened < 0) {
            ContainerLid.open(level, source);
            opened = timer;
            return;
        }
        if (timer - opened < 10) {
            return;
        }
        if (level.getBlockEntity(source) instanceof Container c) {
            for (int i = 0; i < c.getContainerSize() && !worker.inventoryFull(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty()) {
                    ItemStack rest = worker.getInventory().addItem(s.copy());
                    c.setItem(i, rest);
                }
            }
            c.setChanged();
        }
        ContainerLid.close(level, source);
        source = null;
        unloading = true;
    }
}
