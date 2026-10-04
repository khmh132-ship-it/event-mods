package com.echohorror.entity;

import com.echohorror.Config;
import com.echohorror.horror.HorrorUtil;
import com.echohorror.horror.Sanity;
import com.echohorror.network.Fx;
import com.echohorror.network.Net;
import com.echohorror.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * A hallucination. Only ONE player (the target) ever receives this entity from the server —
 * everybody else sees nothing. It never attacks; it watches, follows, and vanishes.
 */
public class PhantomEntity extends PathfinderMob {
    public static final int KIND_WATCHER = 0, KIND_SHADE = 1, KIND_FAKE_PLAYER = 2, KIND_SILENT = 3, KIND_MIMIC = 4;
    public static final int MODE_STARE = 0, MODE_BEHIND = 1, MODE_WALK_AWAY = 2, MODE_CREEP = 3, MODE_STILL = 4;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(PhantomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> SKIN = SynchedEntityData.defineId(PhantomEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private UUID target;
    private int mode;
    private int life = 600;
    private int watchedTicks;
    private int walkTicks;
    private boolean jumpscare;
    private boolean gone;
    private double vanishDistance = 11;
    private int watchLimit = -1;

    public PhantomEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setSilent(true);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    /** Creates and adds a hallucination visible only to {@code target}. */
    public static PhantomEntity spawn(ServerPlayer target, int kind, int mode, Vec3 pos, int life) {
        PhantomEntity e = new PhantomEntity(ModEntities.PHANTOM.get(), target.level());
        e.target = target.getUUID();
        e.mode = mode;
        e.life = life;
        e.entityData.set(KIND, kind);
        e.moveTo(pos.x, pos.y, pos.z, 0, 0);
        e.faceTarget(target);
        target.serverLevel().addFreshEntity(e);
        return e;
    }

    public PhantomEntity skin(UUID skin, String name) {
        entityData.set(SKIN, Optional.ofNullable(skin));
        if (name != null) {
            setCustomName(Component.literal(name));
            setCustomNameVisible(true);
        }
        return this;
    }

    /** How close the target may come before a staring phantom dissolves. */
    public PhantomEntity vanishDistance(double d) {
        this.vanishDistance = d;
        return this;
    }

    /** Ticks of being looked at before it dissolves. */
    public PhantomEntity watchLimit(int ticks) {
        this.watchLimit = ticks;
        return this;
    }

    /** Hangs in the air (window faces). */
    public PhantomEntity floating() {
        setNoGravity(true);
        return this;
    }

    public PhantomEntity withJumpscare() {
        this.jumpscare = true;
        return this;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(KIND, 0);
        entityData.define(SKIN, Optional.empty());
    }

    public int getKind() {
        return entityData.get(KIND);
    }

    public Optional<UUID> getSkin() {
        return entityData.get(SKIN);
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return target != null && target.equals(player.getUUID());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || gone) return;
        ServerPlayer t = target == null ? null : (ServerPlayer) level().getPlayerByUUID(target);
        if (t == null || t.level() != level() || t.distanceToSqr(this) > 128 * 128 || !t.isAlive()) {
            discard();
            return;
        }
        if (--life <= 0) {
            vanish(t, false);
            return;
        }
        double dist = Math.sqrt(t.distanceToSqr(this));
        boolean watched = HorrorUtil.isWatching(t, this, getKind() == KIND_SHADE ? 0.85 : 0.93);
        switch (mode) {
            case MODE_STARE -> {
                faceTarget(t);
                getNavigation().stop();
                if (watched) watchedTicks++;
                int limit = watchLimit > 0 ? watchLimit : getKind() == KIND_SHADE ? 4 : 30;
                if (watchedTicks > limit || dist < vanishDistance)
                    vanish(t, dist < vanishDistance || getKind() != KIND_SHADE && watchedTicks > limit && vanishDistance < 11);
            }
            case MODE_BEHIND -> {
                faceTarget(t);
                if (HorrorUtil.isWatching(t, this, 0.6)) vanish(t, true);
            }
            case MODE_WALK_AWAY -> {
                if (dist < 22 || walkTicks > 0) {
                    walkTicks++;
                    if (walkTicks == 1 || getNavigation().isDone()) {
                        Vec3 away = position().subtract(t.position()).normalize().scale(24).add(position());
                        getNavigation().moveTo(away.x, away.y, away.z, 1.5);
                    }
                    if (walkTicks > 80 || dist < 7) vanish(t, false);
                } else {
                    faceTarget(t);
                }
            }
            case MODE_CREEP -> {
                if (watched || HorrorUtil.isWatching(t, this, 0.55)) {
                    getNavigation().stop();
                    setDeltaMovement(0, getDeltaMovement().y, 0);
                } else {
                    faceTarget(t);
                    getNavigation().moveTo(t, 1.3);
                }
                if (dist < 2.2) {
                    jumpscare = true;
                    vanish(t, true);
                }
            }
            default -> faceTarget(t);
        }
    }

    private void faceTarget(Entity t) {
        double dx = t.getX() - getX(), dz = t.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        double dy = t.getEyeY() - getEyeY();
        setXRot((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180F / Math.PI)));
    }

    /** Disappear. {@code scare} adds a stinger / jumpscare. */
    public void vanish(ServerPlayer t, boolean scare) {
        if (gone) return;
        gone = true;
        Vec3 p = position().add(0, 1, 0);
        t.serverLevel().sendParticles(t, ParticleTypes.LARGE_SMOKE, true, p.x, p.y, p.z, 25, 0.25, 0.6, 0.25, 0.01);
        HorrorUtil.playTo(t, "scare.vanish", p, 0.7f, 0.8f + random.nextFloat() * 0.3f);
        if (scare) {
            HorrorUtil.playAt(t, "scare.stinger", 1.0f, 0.9f + random.nextFloat() * 0.2f);
            Net.fx(t, Fx.SHAKE, 20, 1.0f, "");
            Sanity.add(t, -6f);
            if (jumpscare && Config.JUMPSCARES.get()) {
                int face = getKind() == KIND_WATCHER ? 1 : (getKind() == KIND_MIMIC ? 2 : 0);
                Net.fx(t, Fx.JUMPSCARE, face);
                Sanity.add(t, -6f);
            }
        } else {
            Sanity.add(t, -2f);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof ServerPlayer sp && sp.getUUID().equals(target)) {
            vanish(sp, false);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity e) {}

    @Override
    protected void pushEntities() {}

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public void checkDespawn() {}

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
