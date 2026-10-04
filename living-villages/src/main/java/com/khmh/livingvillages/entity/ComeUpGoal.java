package com.khmh.livingvillages.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Somebody left down a mine or a cave with nothing to do there (a miner moved to another job in his tunnel, a
 * villager who fell into a cave) makes his way back up to the bell, instead of idling in the dark for good.
 */
class ComeUpGoal extends Goal {
    private static final int BELOW = 5;

    private final VillageWorker worker;
    private int timer;

    ComeUpGoal(VillageWorker worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private BlockPos bell() {
        return worker.village().map(v -> v.center()).orElse(null);
    }

    @Override
    public boolean canUse() {
        BlockPos bell = bell();
        return bell != null && worker.job() != WorkerJob.MINER && worker.job() != WorkerJob.BUILDER
                && !worker.isSleeping() && worker.getY() < bell.getY() - BELOW;
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos bell = bell();
        return bell != null && worker.job() != WorkerJob.MINER && worker.getY() < bell.getY() - 3;
    }

    @Override
    public void start() {
        timer = 0;
        worker.setStatus("coming up");
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        worker.setStatus("coming up");
        BlockPos bell = bell();
        if (bell != null && (timer++ % 40 == 0 || worker.getNavigation().isDone())) {
            worker.getNavigation().moveTo(bell.getX() + 0.5, bell.getY(), bell.getZ() + 0.5, 0.6);
        }
    }

    @Override
    public void stop() {
        worker.getNavigation().stop();
    }
}
