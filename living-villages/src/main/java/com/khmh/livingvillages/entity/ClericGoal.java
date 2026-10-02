package com.khmh.livingvillages.entity;

import net.minecraft.core.BlockPos;

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
        altar = null;
        if (patient == null && worker.level() instanceof ServerLevel level
                && level.getGameTime() - lastBlessing > 3000 && level.random.nextInt(4) == 0) {
            lastBlessing = level.getGameTime();
            altar = blessable(level, v);
        }
        return patient != null || altar != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (altar != null) {
            return timer < 600;
        }
        return patient != null && patient.isAlive() && patient.getHealth() < patient.getMaxHealth();
    }

    /** A chest holding the best tool, weapon or armour piece in store that has no enchantment yet. */
    private BlockPos altar;
    private long lastBlessing;

    @javax.annotation.Nullable
    private static BlockPos blessable(ServerLevel level, Village v) {
        BlockPos best = null;
        int bestScore = 0;
        for (BlockPos p : com.khmh.livingvillages.stock.Stockpile.containers(level, v)) {
            if (level.getBlockEntity(p) instanceof net.minecraft.world.Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack st = c.getItem(i);
                    if (!st.isEmpty() && st.isEnchantable() && !st.isEnchanted() && Gear.score(st) > bestScore) {
                        bestScore = Gear.score(st);
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Now and then the cleric blesses the best plain piece of gear in store: it comes out enchanted, as from an
     * enchanting table (better gear and luck give better enchantments).
     */
    private void bless(ServerLevel level) {
        worker.setStatus("blessing gear");
        BlockPos at = altar;
        if (worker.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) > 6.25 || !Reach.sees(worker, at)) {
            if (timer % 20 == 1) {
                worker.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.6);
            }
            return;
        }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
        if (level.getBlockEntity(at) instanceof net.minecraft.world.Container c) {
            int slot = -1;
            for (int i = 0; i < c.getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = c.getItem(i);
                if (!st.isEmpty() && st.isEnchantable() && !st.isEnchanted()
                        && (slot < 0 || Gear.score(st) > Gear.score(c.getItem(slot)))) {
                    slot = i;
                }
            }
            if (slot >= 0) {
                net.minecraft.world.item.ItemStack plain = c.getItem(slot);
                int power = 8 + level.random.nextInt(23); // up to a full enchanting table's 30 levels
                net.minecraft.world.item.ItemStack blessed = net.minecraft.world.item.enchantment.EnchantmentHelper
                        .enchantItem(level.random, plain.copy(), power, false);
                c.setItem(slot, blessed);
                c.setChanged();
                worker.swing(InteractionHand.MAIN_HAND);
                level.sendParticles(ParticleTypes.ENCHANT, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 30, 0.4,
                        0.4, 0.4, 0.5);
                com.khmh.livingvillages.LivingVillages.LOGGER.info("A cleric blessed {}", blessed);
            }
        }
        altar = null;
        timer = 600;
    }

    @Override
    public void start() {
        timer = 0;
    }

    @Override
    public void tick() {
        timer++;
        if (altar != null) {
            if (worker.level() instanceof ServerLevel level) {
                bless(level);
            }
            return;
        }
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
