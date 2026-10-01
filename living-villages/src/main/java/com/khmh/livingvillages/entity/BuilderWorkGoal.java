package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.EnumSet;

/** Walks to the village's construction site and works there; a builder on site triples construction speed. */
public class BuilderWorkGoal extends Goal {
    private final VillageWorker worker;
    private Building target;
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
        if (village == null) {
            target = null;
            return;
        }
        BlockPos stand = VillageWorker.standAt(worker.level(), target.entrance());
        BoundingBox box = target.box();
        double cx = (box.minX() + box.maxX()) / 2.0 + 0.5, cz = (box.minZ() + box.maxZ()) / 2.0 + 0.5;
        if (worker.distanceToSqr(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5) > 25) {
            if (--repath <= 0) {
                worker.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.6);
                repath = 40;
            }
            return;
        }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(cx, target.groundY() + 2, cz);
        village.reportWorking(target.id(), worker.level().getGameTime());
        if (worker.tickCount % 20 == 0) {
            worker.swing(InteractionHand.MAIN_HAND);
        }
    }
}
