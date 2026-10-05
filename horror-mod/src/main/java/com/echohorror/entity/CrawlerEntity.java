package com.echohorror.entity;

import com.echohorror.horror.HorrorUtil;
import com.echohorror.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Ползун. Eyeless, crawls on all fours and up walls, hunts by sound: a player sneaking more than a few blocks
 * away is invisible to it.
 */
public class CrawlerEntity extends Monster {
    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(CrawlerEntity.class, EntityDataSerializers.BOOLEAN);

    public CrawlerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.ATTACK_DAMAGE, 4.5)
                .add(Attributes.MOVEMENT_SPEED, 0.33).add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CLIMBING, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.45f));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 5, false, false,
                p -> HorrorUtil.isSurvivalLike((Player) p) && (!p.isCrouching() || p.distanceTo(this) < 5)));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            entityData.set(CLIMBING, horizontalCollision);
            // a sneaking target that slipped away is forgotten
            if (getTarget() instanceof Player p && p.isCrouching() && p.distanceTo(this) > 7 && getLastHurtByMob() != p) {
                setTarget(null);
            }
        }
    }

    private Vec3 heard;
    private int investigate;

    /** Called by the noise system: run towards the sound; if the source is close, it is found. */
    public void hear(Vec3 pos, ServerPlayer source) {
        if (getTarget() != null) return;
        if (source != null && HorrorUtil.isSurvivalLike(source) && source.distanceTo(this) < 10) {
            setTarget(source);
            playSound(ModSounds.get("entity.crawler.click"), 1.5f, 1.3f);
            return;
        }
        heard = pos;
        investigate = 200;
        getNavigation().moveTo(pos.x, pos.y, pos.z, 1.35);
        if (random.nextFloat() < 0.5f) playSound(ModSounds.get("entity.crawler.click"), 1.2f, 1.2f);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && heard != null && getTarget() == null) {
            if (--investigate <= 0 || distanceToSqr(heard) < 4) heard = null;
            else if (getNavigation().isDone()) getNavigation().moveTo(heard.x, heard.y, heard.z, 1.35);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (source.getEntity() instanceof ServerPlayer sp) com.echohorror.story.Achievements.award(sp, "crawler_kill");
    }

    @Override
    public boolean onClimbable() {
        return entityData.get(CLIMBING);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.get("entity.crawler.click");
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) {
        return ModSounds.get("entity.crawler.hurt");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.get("entity.crawler.death");
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SPIDER_STEP, 0.15f, 0.6f);
    }
}
