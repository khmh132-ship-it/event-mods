package com.echohorror.entity;

import com.echohorror.horror.ChatMemory;
import com.echohorror.horror.HorrorUtil;
import com.echohorror.horror.Sanity;
import com.echohorror.horror.VoiceBridge;
import com.echohorror.network.Fx;
import com.echohorror.network.Net;
import com.echohorror.registry.ModEntities;
import com.echohorror.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * Двойник. Looks exactly like one of the players (skin + name tag), walks like a player, talks in chat
 * with that player's own remembered words — until you get close.
 */
public class MimicEntity extends Monster {
    private static final EntityDataAccessor<Optional<UUID>> SKIN = SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> REVEALED = SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.BOOLEAN);

    private String copiedName = "";
    private int talkCooldown = 200;
    private boolean daylightImmune;
    private boolean npc;       // pretends to be a survivor and talks until it gives itself away
    private int talks;
    private int lastTalk = -1000;

    private static final String[] NPC_LINES = {
            "Живой? Живой... Я — Пётр Кузьмич. Я тут один остался. Не ходите в церковь ночью: там колокол звонит сам.",
            "Язык колокола Никодим искал, да не нашёл. Он у меня в подполе, в доме у колодца. Только не зови меня по имени, слышишь? Оно запоминает.",
            "А ты ведь меня не узнал? А я тебя узнал. Голос у тебя хороший. Очень хороший голос."};

    /** Turns this mimic into a "survivor" who talks to players. */
    public MimicEntity asSurvivor(String name) {
        npc = true;
        daylightImmune = true;
        setPersistenceRequired();
        disguise(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name);
        return this;
    }

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (!npc || isRevealed() || hand != net.minecraft.world.InteractionHand.MAIN_HAND) return super.mobInteract(player, hand);
        if (!level().isClientSide && tickCount - lastTalk > 100) { // let him finish the sentence
            lastTalk = tickCount;
            String line = NPC_LINES[Math.min(talks, NPC_LINES.length - 1)];
            player.sendSystemMessage(Component.translatable("chat.type.text", copiedName, line));
            level().playSound(null, this, ModSounds.get("voice.kuzmich" + Math.min(talks + 1, NPC_LINES.length)), SoundSource.NEUTRAL, 1.3f, 1f);
            talks++;
            if (talks >= NPC_LINES.length) {
                net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) level();
                com.echohorror.horror.Scheduler.schedule(50, () -> {
                    if (isAlive() && !isRevealed()) {
                        reveal();
                        setTarget(player);
                    }
                });
            }
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
    }

    public MimicEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 34).add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.ARMOR, 2);
    }

    /** Spawns a mimic wearing {@code copied}'s face. */
    public static MimicEntity spawnAs(ServerLevel level, Vec3 pos, ServerPlayer copied) {
        MimicEntity m = new MimicEntity(ModEntities.MIMIC.get(), level);
        m.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360f, 0);
        if (copied != null) m.disguise(copied.getUUID(), copied.getGameProfile().getName());
        level.addFreshEntity(m);
        return m;
    }

    public MimicEntity daylightImmune() {
        daylightImmune = true;
        setPersistenceRequired();
        return this;
    }

    public void disguise(UUID skin, String name) {
        entityData.set(SKIN, Optional.ofNullable(skin));
        copiedName = name == null ? "" : name;
        if (!copiedName.isEmpty()) {
            setCustomName(Component.literal(copiedName));
            setCustomNameVisible(true);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SKIN, Optional.empty());
        entityData.define(REVEALED, false);
    }

    public Optional<UUID> getSkin() {
        return entityData.get(SKIN);
    }

    public boolean isRevealed() {
        return entityData.get(REVEALED);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.45, true) {
            @Override
            public boolean canUse() {
                return isRevealed() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return isRevealed() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(3, new StalkGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 32f, 1f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                p -> HorrorUtil.isSurvivalLike((Player) p) && (!npc || isRevealed())));
    }

    /** Approaches slowly, stops at a "conversation" distance and just... stands there. */
    private class StalkGoal extends Goal {
        @Override
        public boolean canUse() {
            return !isRevealed() && getTarget() != null;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30f, 30f);
            double d = distanceTo(t);
            if (d > 7) getNavigation().moveTo(t, 0.9);
            else getNavigation().stop();
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        LivingEntity t = getTarget();
        if (!isRevealed()) {
            if (!npc && t != null && distanceTo(t) < 4.5 && hasLineOfSight(t)) reveal();
            if (!npc && t instanceof ServerPlayer sp && --talkCooldown <= 0 && distanceTo(sp) < 40) {
                talkCooldown = 300 + random.nextInt(500);
                speak(sp);
            }
            if (!daylightImmune && level().isDay() && level().canSeeSky(blockPosition()) && tickCount > 100) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.3, 0.6, 0.3, 0.01);
                discard();
            }
        }
    }

    /** Uses the copied player's real voice (voice chat) or their real chat lines. */
    private void speak(ServerPlayer listener) {
        UUID skin = getSkin().orElse(null);
        if (skin != null && VoiceBridge.hasClip(skin) && VoiceBridge.play(listener, skin, position().add(0, 1.6, 0))) return;
        if (skin != null && !copiedName.isEmpty()) {
            String text = ChatMemory.randomOf(random, skin).map(ChatMemory.Line::text)
                    .orElse(new String[]{"иди сюда", "я тут", "ты чего стоишь?", "подойди", "всё нормально, иди сюда"}[random.nextInt(5)]);
            listener.sendSystemMessage(Component.translatable("chat.type.text", copiedName, text));
        }
    }

    public void reveal() {
        if (isRevealed()) return;
        entityData.set(REVEALED, true);
        setCustomName(null);
        setCustomNameVisible(false);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.36);
        level().playSound(null, blockPosition(), ModSounds.get("entity.mimic.reveal"), SoundSource.HOSTILE, 2.0f, 1.0f);
        for (Player p : level().players()) {
            if (p instanceof ServerPlayer sp && sp.distanceTo(this) < 16) {
                Net.fx(sp, Fx.SHAKE, 25, 1.2f, "");
                Net.fx(sp, Fx.GLITCH, 8);
                Sanity.add(sp, -8f);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide) reveal();
        return r;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isRevealed() ? ModSounds.get("entity.mimic.idle") : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) {
        return isRevealed() ? ModSounds.get("entity.crawler.hurt") : SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.get("entity.crawler.death");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getSkin().ifPresent(u -> tag.putUUID("Skin", u));
        tag.putString("CopiedName", copiedName);
        tag.putBoolean("Revealed", isRevealed());
        tag.putBoolean("DayImmune", daylightImmune);
        tag.putBoolean("Npc", npc);
        tag.putInt("Talks", talks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        copiedName = tag.getString("CopiedName");
        daylightImmune = tag.getBoolean("DayImmune");
        npc = tag.getBoolean("Npc");
        talks = tag.getInt("Talks");
        if (tag.hasUUID("Skin")) entityData.set(SKIN, Optional.of(tag.getUUID("Skin")));
        if (tag.getBoolean("Revealed")) {
            entityData.set(REVEALED, true);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.36);
        }
    }
}
