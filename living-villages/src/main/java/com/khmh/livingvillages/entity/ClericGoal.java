package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.village.Village;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.EnumSet;

/** The cleric goes round the wounded (workers and villagers alike) and tends to them until they are well. */
public class ClericGoal extends Goal {
    private final VillageWorker worker;
    private LivingEntity patient;
    private int timer;
    private int cooldown;

    public ClericGoal(VillageWorker worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (worker.job() != WorkerJob.CLERIC || --cooldown > 0) {
            return false;
        }
        cooldown = 20;
        Village v = worker.village().orElse(null);
        if (v == null) {
            return false;
        }
        patient = worker.level().getEntitiesOfClass(LivingEntity.class, new AABB(v.center()).inflate(v.radius()),
                        e -> e.isAlive() && e != worker && e.getHealth() < e.getMaxHealth()
                                && (e instanceof VillageWorker || e instanceof AbstractVillager))
                .stream().min(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth())).orElse(null);
        return patient != null;
    }

    @Override
    public boolean canContinueToUse() {
        return patient != null && patient.isAlive() && patient.getHealth() < patient.getMaxHealth();
    }

    @Override
    public void start() {
        timer = 0;
    }

    @Override
    public void tick() {
        timer++;
        worker.setStatus("tending " + patient.getName().getString());
        if (worker.distanceToSqr(patient) > 6) {
            if (timer % 20 == 1) {
                worker.getNavigation().moveTo(patient, 0.65);
            }
            return;
        }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(patient);
        if (timer % 30 == 0) {
            patient.heal(2.0F);
            worker.swing(InteractionHand.MAIN_HAND);
            if (worker.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + 1.8, patient.getZ(), 1, 0.2, 0.2,
                        0.2, 0);
            }
        }
    }
}
