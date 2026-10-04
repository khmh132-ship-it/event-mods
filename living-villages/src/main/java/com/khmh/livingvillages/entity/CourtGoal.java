package com.khmh.livingvillages.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Two villagers the village has paired off walk up to each other and stand together with hearts over their
 * heads, as vanilla villagers do before a child is born (the birth itself is the village's: Births).
 */
class CourtGoal extends Goal {
    private final VillageWorker worker;
    private int timer;

    CourtGoal(VillageWorker worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private Entity mate() {
        return worker.mate() != null && worker.level() instanceof ServerLevel sl ? sl.getEntity(worker.mate()) : null;
    }

    @Override
    public boolean canUse() {
        Entity mate = mate();
        return mate != null && mate.isAlive() && !worker.isSleeping();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        timer = 0;
    }

    @Override
    public void stop() {
        worker.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Entity mate = mate();
        if (mate == null) {
            return;
        }
        timer++;
        worker.setStatus("with " + (mate.hasCustomName() ? mate.getCustomName().getString() : "a sweetheart"));
        worker.getLookControl().setLookAt(mate, 30.0F, 30.0F);
        if (worker.distanceToSqr(mate) > 4.0) {
            if (timer % 20 == 1) {
                worker.getNavigation().moveTo(mate.getX(), mate.getY(), mate.getZ(), 0.6);
            }
        } else {
            worker.getNavigation().stop();
            if (timer % 10 == 0 && worker.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HEART, worker.getX(), worker.getEyeY() + 0.5, worker.getZ(), 1,
                        0.2, 0.1, 0.2, 0.0);
            }
        }
    }
}
