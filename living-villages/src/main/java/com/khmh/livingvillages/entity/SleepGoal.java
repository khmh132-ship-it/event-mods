package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** At night workers stop, go home and sleep in their own bed; at dawn they get up and go back to work. */
public class SleepGoal extends Goal {
    private final VillageWorker worker;
    private BlockPos bed;
    private int timer;

    public SleepGoal(VillageWorker worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return worker.level().isNight() && worker.village().isPresent();
    }

    @Override
    public boolean canContinueToUse() {
        return worker.level().isNight();
    }

    @Override
    public void start() {
        timer = 0;
        bed = null;
    }

    @Override
    public void stop() {
        if (worker.isSleeping()) {
            worker.stopSleeping();
        }
        bed = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        worker.setStatus(worker.isSleeping() ? "sleeping" : "going to bed");
        if (worker.isSleeping() || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        Village village = worker.village().orElse(null);
        if (village == null) {
            return;
        }
        if (bed == null) {
            bed = Beds.of(level, village, worker);
            if (bed == null) {
                return; // no free bed: stays put and rests where he is
            }
        }
        timer++;
        if (worker.distanceToSqr(bed.getX() + 0.5, bed.getY() + 0.5, bed.getZ() + 0.5) <= 4.0 || timer > 900) {
            worker.getNavigation().stop();
            worker.startSleeping(bed);
        } else if (timer % 20 == 1) {
            worker.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 0.6);
        }
    }
}
