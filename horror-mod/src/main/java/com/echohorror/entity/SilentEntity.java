package com.echohorror.entity;

import com.echohorror.horror.HorrorUtil;
import com.echohorror.horror.Sanity;
import com.echohorror.network.Fx;
import com.echohorror.network.Net;
import com.echohorror.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Немая (subject N-3). Moves ONLY when nobody is looking at her. Never animates — she is simply closer
 * every time you turn back.
 */
public class SilentEntity extends Monster {
    private int attackCooldown;
    private int scrapeCooldown;
    private int musicCooldown = 200;
    private float frozenYaw;
    private boolean observed;

    public SilentEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 70).add(Attributes.ATTACK_DAMAGE, 9)
                .add(Attributes.MOVEMENT_SPEED, 0.36).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.ARMOR, 8)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        // fully custom brain in customServerAiStep
    }

    public boolean isObserved() {
        return observed;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        observed = false;
        for (Player p : level().players()) {
            if (p.isSpectator() || p.distanceToSqr(this) > 64 * 64) continue;
            if (HorrorUtil.isWatching(p, this, 0.5)) {
                observed = true;
                break;
            }
        }
        Player target = level().getNearestPlayer(this, 40);
        if (target != null && !HorrorUtil.isSurvivalLike(target)) target = null;
        if (attackCooldown > 0) attackCooldown--;

        if (observed) {
            getNavigation().stop();
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            setYRot(frozenYaw);
            yBodyRot = frozenYaw;
            yHeadRot = frozenYaw;
            return;
        }
        if (target == null) return;
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        frozenYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        setYRot(frozenYaw);
        yBodyRot = frozenYaw;
        yHeadRot = frozenYaw;
        getNavigation().moveTo(target, 1.0);
        if (--scrapeCooldown <= 0) {
            scrapeCooldown = 8;
            level().playSound(null, blockPosition(), ModSounds.get("entity.silent.move"), SoundSource.HOSTILE, 0.5f, 0.8f + random.nextFloat() * 0.3f);
        }
        if (distanceTo(target) < 1.9 && attackCooldown <= 0) {
            attackCooldown = 25;
            doHurtTarget(target);
            level().playSound(null, blockPosition(), ModSounds.get("entity.silent.attack"), SoundSource.HOSTILE, 1.5f, 1f);
            if (target instanceof ServerPlayer sp) {
                Net.fx(sp, Fx.SHAKE, 20, 1.5f, "");
                Sanity.add(sp, -10f);
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && --musicCooldown <= 0) {
            musicCooldown = 700 + random.nextInt(400);
            if (level().getNearestPlayer(this, 20) != null) {
                level().playSound(null, blockPosition(), ModSounds.get("scare.music_box"), SoundSource.HOSTILE, 0.8f, 0.9f);
            }
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity e) {}

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }
}
