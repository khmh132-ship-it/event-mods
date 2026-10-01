package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.building.Construction;
import com.khmh.livingvillages.config.LVConfig;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;

/**
 * The builder walks around the construction site and places every block himself: he goes within reach of the
 * next block, places it, and moves on. If he cannot get there (walled in, cut off) he climbs over, i.e. is moved
 * to the surface next to the target.
 */
public class BuilderWorkGoal extends Goal {
    private static final double REACH_H = 5.0;
    private static final int STUCK_TICKS = 160;

    private final VillageWorker worker;
    private Building target;
    private int cooldown;
    private int stuck;
    private int repath;

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
        cooldown = 0;
        stuck = 0;
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
        long now = level.getGameTime();
        village.reportWorking(target.id(), now);
        if (inReach(next)) {
            stuck = 0;
            worker.getNavigation().stop();
            worker.getLookControl().setLookAt(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5);
            if (--cooldown > 0) {
                return;
            }
            cooldown = Math.max(1, LVConfig.BUILD_INTERVAL.get() / 3);
            if (worker.getBoundingBox().intersects(new AABB(next))) {
                stepAside(level, next);
            }
            Construction.buildOne(level, village, target, now);
            worker.swing(InteractionHand.MAIN_HAND);
            return;
        }
        if (++stuck > STUCK_TICKS) {
            stepAside(level, next);
            stuck = 0;
            return;
        }
        if (--repath <= 0) {
            worker.getNavigation().moveTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 0.6);
            repath = 20;
        }
    }

    private boolean inReach(BlockPos p) {
        double dx = worker.getX() - (p.getX() + 0.5), dz = worker.getZ() - (p.getZ() + 0.5);
        double dy = p.getY() - worker.getY();
        return dx * dx + dz * dz <= REACH_H * REACH_H && dy > -5 && dy < 6;
    }

    /** Moves the builder onto the surface beside the target, outside the block about to be placed. */
    private void stepAside(ServerLevel level, BlockPos next) {
        BlockPos best = null;
        for (BlockPos p : BlockPos.betweenClosed(next.offset(-2, 0, -2), next.offset(2, 0, 2))) {
            if (p.getX() == next.getX() && p.getZ() == next.getZ()) {
                continue;
            }
            BlockPos stand = VillageWorker.standAt(level, p);
            if (stand.getY() >= next.getY() - 4 && stand.getY() <= next.getY() + 5
                    && (best == null || stand.distSqr(next) < best.distSqr(next))) {
                best = stand.immutable();
            }
        }
        if (best == null) {
            best = VillageWorker.standAt(level, target.entrance());
        }
        worker.getNavigation().stop();
        worker.teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
    }
}
