package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.building.Construction;
import com.khmh.livingvillages.config.LVConfig;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The builder walks around the construction site and places every block himself. He walks to within reach of
 * the next block; when he cannot get any closer (walled in, the block is high up) he stays where he is, looks at
 * the spot and places it from there, through whatever is in between.
 */
public class BuilderWorkGoal extends Goal {
    private static final double REACH = 4.5;
    /** From this far he still places through obstacles once he cannot get closer. */
    private static final double FAR_REACH = 16.0;
    private static final int NO_PROGRESS_TICKS = 30;
    /** Truly cut off: he works from wherever he is rather than stall the village. */
    private static final int GIVE_UP_TICKS = 400;

    private final VillageWorker worker;
    private Building target;
    private BlockPos lastNext;
    private double bestDist;
    private int noProgress;
    private int cooldown;
    private int repath;
    private int blockedBySelf;

    public BuilderWorkGoal(VillageWorker worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (worker.job() != WorkerJob.BUILDER) {
            return false;
        }
        target = worker.village().flatMap(v -> v.buildings().stream().filter(b -> !b.isComplete()).findFirst())
                .orElse(null);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && !target.isComplete() && worker.job() == WorkerJob.BUILDER;
    }

    @Override
    public void start() {
        lastNext = null;
        cooldown = 0;
        repath = 0;
    }

    @Override
    public void stop() {
        target = null;
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
        BlockPos next = Construction.nextTarget(level, target);
        if (next == null) {
            return;
        }
        if (!next.equals(lastNext)) {
            lastNext = next;
            bestDist = Double.MAX_VALUE;
            noProgress = 0;
        }
        long now = level.getGameTime();
        village.reportWorking(target.id(), now);
        double dist = Math.sqrt(worker.distanceToSqr(Vec3.atCenterOf(next)));
        if (dist < bestDist - 0.3) {
            bestDist = dist;
            noProgress = 0;
        } else {
            noProgress++;
        }

        boolean closeEnough = dist <= REACH
                || noProgress > NO_PROGRESS_TICKS && worker.getNavigation().isDone() && dist <= FAR_REACH
                || noProgress > GIVE_UP_TICKS;
        if (closeEnough) {
            worker.getNavigation().stop();
            worker.getLookControl().setLookAt(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5);
            if (--cooldown > 0) {
                return;
            }
            cooldown = Math.max(2, LVConfig.BUILD_INTERVAL.get() / 2);
            if (worker.getBoundingBox().intersects(new AABB(next))) {
                if (++blockedBySelf < 40) {
                    stepOff(next);
                    return;
                }
                worker.setPos(worker.getX(), next.getY() + 1, worker.getZ()); // climbs onto it
            }
            blockedBySelf = 0;
            Construction.buildOne(level, village, target, now);
            worker.swing(InteractionHand.MAIN_HAND);
            return;
        }
        if (--repath <= 0) {
            // Paths to the nearest reachable spot if the block itself cannot be reached.
            Path path = worker.getNavigation().createPath(next, 1);
            if (path != null) {
                worker.getNavigation().moveTo(path, 0.6);
            }
            repath = 20;
        }
    }

    /** The next block goes where the builder stands: he takes a step back first. */
    private void stepOff(BlockPos next) {
        BlockPos away = worker.blockPosition().relative(worker.getDirection().getOpposite(), 2);
        worker.getNavigation().moveTo(away.getX() + 0.5, worker.getY(), away.getZ() + 0.5, 0.6);
    }
}
