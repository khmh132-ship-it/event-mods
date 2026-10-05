package com.echohorror.entity;

import com.echohorror.horror.ChatMemory;
import com.echohorror.horror.HorrorUtil;
import com.echohorror.horror.Sanity;
import com.echohorror.horror.VoiceBridge;
import com.echohorror.network.Fx;
import com.echohorror.network.Net;
import com.echohorror.registry.ModEntities;
import com.echohorror.registry.ModSounds;
import com.echohorror.story.StoryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * ОТГОЛОСОК — the Echo itself. Untouchable while it can hear itself. Only the clean tone of a bell
 * (vanilla bells around the arena) stuns it and makes it vulnerable for a few seconds.
 */
public class EchoBossEntity extends Monster {
    private static final EntityDataAccessor<Boolean> STUNNED = SynchedEntityData.defineId(EchoBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> FACE = SynchedEntityData.defineId(EchoBossEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(Component.literal("ОТГОЛОСОК"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true).setCreateWorldFog(true);

    private final List<BlockPos> bells = new ArrayList<>();
    private final Map<BlockPos, Long> bellUsed = new HashMap<>();
    private BlockPos home;
    private boolean scaled;
    private int phase = 1;
    private int stunTicks, stunImmune;
    private float shriekCd = 160, summonCd = 260, teleportCd = 300;
    private int voiceCd = 100, hintCd, lonelyTicks;
    private float damageThisStun;

    public EchoBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 500;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 450).add(Attributes.ATTACK_DAMAGE, 13)
                .add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.ARMOR, 10)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.ATTACK_KNOCKBACK, 1.5);
    }

    public static EchoBossEntity spawn(ServerLevel level, BlockPos pos) {
        EchoBossEntity b = new EchoBossEntity(ModEntities.ECHO_BOSS.get(), level);
        b.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        b.home = pos;
        level.addFreshEntity(b);
        return b;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STUNNED, false);
        entityData.define(FACE, java.util.Optional.empty());
    }

    public java.util.Optional<java.util.UUID> getFace() {
        return entityData.get(FACE);
    }

    public boolean isStunned() {
        return entityData.get(STUNNED);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return !isStunned() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !isStunned() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 40f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                p -> HorrorUtil.isSurvivalLike((Player) p)));
    }

    private List<ServerPlayer> arenaPlayers() {
        List<ServerPlayer> l = new ArrayList<>();
        for (Player p : level().players()) {
            if (p instanceof ServerPlayer sp && sp.distanceToSqr(this) < 48 * 48 && !sp.isSpectator()) l.add(sp);
        }
        return l;
    }

    private void scanBells() {
        bells.clear();
        BlockPos c = home != null ? home : blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-26, -6, -26), c.offset(26, 14, 26))) {
            if (level().getBlockState(p).getBlock() instanceof BellBlock) bells.add(p.immutable());
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        List<ServerPlayer> players = arenaPlayers();
        if (home == null) home = blockPosition();
        if (!scaled) {
            scaled = true;
            int n = Math.max(1, players.size());
            float max = 450f + 220f * (n - 1);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
            setHealth(max);
            scanBells();
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());

        // leash to the arena
        if (home != null && distanceToSqr(Vec3.atCenterOf(home)) > 34 * 34) {
            teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        }
        // nobody here: slowly reset
        if (players.isEmpty()) {
            if (++lonelyTicks > 1200) {
                setHealth(getMaxHealth());
                lonelyTicks = 0;
            }
            return;
        }
        lonelyTicks = 0;

        int newPhase = getHealth() / getMaxHealth() > 0.66f ? 1 : getHealth() / getMaxHealth() > 0.33f ? 2 : 3;
        if (newPhase != phase) {
            phase = newPhase;
            level.playSound(null, blockPosition(), ModSounds.get("entity.boss.roar"), SoundSource.HOSTILE, 4f, 0.8f);
            for (ServerPlayer sp : players) {
                Net.fx(sp, Fx.SHAKE, 40, 2f, "");
                Net.fx(sp, Fx.SCREEN_TEXT, 30, 0f, phase == 2 ? "ОНО ВСПОМИНАЕТ ВАС" : "ОНО ЗНАЕТ ВАШИ ИМЕНА");
            }
            if (phase == 3 && !players.isEmpty()) {
                // the last phase wears one of you
                ServerPlayer face = players.get(random.nextInt(players.size()));
                entityData.set(FACE, java.util.Optional.of(face.getUUID()));
                for (ServerPlayer sp : players) {
                    sp.displayClientMessage(Component.literal("Оно надело лицо " + face.getGameProfile().getName() + ".")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                }
            }
        }

        // bells
        for (BlockPos bp : bells) {
            BlockEntity be = level.getBlockEntity(bp);
            if (be instanceof BellBlockEntity bell && bell.shaking && bell.ticks <= 1) onBellRung(bp, players);
        }

        if (stunImmune > 0) stunImmune--;
        if (isStunned()) {
            getNavigation().stop();
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            if (tickCount % 4 == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 3, getZ(), 3, 0.6, 1.0, 0.6, 0.02);
            if (--stunTicks <= 0) {
                entityData.set(STUNNED, false);
                stunImmune = 60;
                level.playSound(null, blockPosition(), ModSounds.get("entity.boss.roar"), SoundSource.HOSTILE, 3f, 1.1f);
            }
            return;
        }

        float speed = phase == 3 ? 1.6f : phase == 2 ? 1.3f : 1f;
        shriekCd -= speed;
        summonCd -= speed;
        voiceCd--;
        if (phase >= 2) teleportCd -= speed;
        if (hintCd > 0) hintCd--;

        if (shriekCd <= 0) {
            shriekCd = 280;
            shriek(players);
        }
        if (summonCd <= 0) {
            summonCd = 420;
            summon(level, players);
        }
        if (teleportCd <= 0) {
            teleportCd = 300;
            teleportBehind(players);
        }
        if (voiceCd <= 0) {
            voiceCd = 180 + random.nextInt(160);
            speak(players);
        }
    }

    private void onBellRung(BlockPos bp, List<ServerPlayer> players) {
        long now = level().getGameTime();
        Long used = bellUsed.get(bp);
        if (used != null && now - used < 600) {
            for (ServerPlayer sp : players) {
                sp.displayClientMessage(Component.literal("Этот колокол ещё дрожит. Звоните в другой!").withStyle(ChatFormatting.GOLD), true);
            }
            return;
        }
        if (isStunned() || stunImmune > 0) return;
        bellUsed.put(bp, now);
        stunTicks = phase == 3 ? 120 : 170;
        damageThisStun = 0;
        entityData.set(STUNNED, true);
        getNavigation().stop();
        level().playSound(null, blockPosition(), ModSounds.get("entity.boss.stun"), SoundSource.HOSTILE, 4f, 1f);
        for (ServerPlayer sp : players) {
            Net.fx(sp, Fx.WHITE_FLASH, 10);
            sp.displayClientMessage(Component.literal("ОТГОЛОСОК ОГЛУШЁН ЗВОНОМ — БЕЙТЕ!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
        }
    }

    private void shriek(List<ServerPlayer> players) {
        level().playSound(null, blockPosition(), ModSounds.get("entity.boss.roar"), SoundSource.HOSTILE, 4f, 0.9f + random.nextFloat() * 0.2f);
        for (ServerPlayer sp : players) {
            if (sp.distanceTo(this) > 32 || !HorrorUtil.isSurvivalLike(sp)) continue;
            sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            sp.hurt(damageSources().magic(), 3f);
            Net.fx(sp, Fx.SHAKE, 30, 2.0f, "");
            Sanity.add(sp, -5f);
        }
    }

    private void summon(ServerLevel level, List<ServerPlayer> players) {
        level.playSound(null, blockPosition(), ModSounds.get("entity.boss.voice"), SoundSource.HOSTILE, 3f, 0.8f);
        int crawlers = phase == 2 ? 1 : 2 + players.size() / 2;
        for (int i = 0; i < crawlers; i++) {
            Vec3 p = position().add(random.nextGaussian() * 6, 0.5, random.nextGaussian() * 6);
            HorrorUtil.ground(level, (int) Math.floor(p.x), (int) Math.floor(p.z), getBlockY() + 2, 4, 6).ifPresent(g -> {
                CrawlerEntity c = new CrawlerEntity(ModEntities.CRAWLER.get(), level);
                c.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, random.nextFloat() * 360, 0);
                level.addFreshEntity(c);
                level.sendParticles(ParticleTypes.SCULK_SOUL, c.getX(), c.getY() + 0.5, c.getZ(), 15, 0.4, 0.4, 0.4, 0.05);
            });
        }
        if (phase >= 2) {
            List<ServerPlayer> copy = new ArrayList<>(players);
            Collections.shuffle(copy);
            for (int i = 0; i < Math.min(phase == 3 ? 3 : 2, copy.size()); i++) {
                ServerPlayer victim = copy.get(i);
                Vec3 p = position().add(random.nextGaussian() * 5, 0, random.nextGaussian() * 5);
                HorrorUtil.ground(level, (int) Math.floor(p.x), (int) Math.floor(p.z), getBlockY() + 2, 4, 6).ifPresent(g -> {
                    MimicEntity m = MimicEntity.spawnAs(level, Vec3.atBottomCenterOf(g), victim).daylightImmune();
                    level.sendParticles(ParticleTypes.SCULK_SOUL, m.getX(), m.getY() + 1, m.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
                });
            }
            for (ServerPlayer sp : players) {
                sp.displayClientMessage(Component.literal("Оно надело ваши лица.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
            }
        }
    }

    private void teleportBehind(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        ServerPlayer victim = players.get(random.nextInt(players.size()));
        Vec3 b = HorrorUtil.behind(victim, 3.5);
        HorrorUtil.ground((ServerLevel) level(), (int) Math.floor(b.x), (int) Math.floor(b.z), victim.getBlockY() + 1, 3, 4).ifPresent(g -> {
            ((ServerLevel) level()).sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2, getZ(), 40, 0.6, 1.5, 0.6, 0.02);
            teleportTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            Net.fx(victim, Fx.BLACKOUT, 8);
            HorrorUtil.playAt(victim, "scare.stinger", 1f, 0.8f);
            HorrorUtil.playTo(victim, "scare.breath", position().add(0, 3, 0), 1f, 0.6f);
            setTarget(victim);
        });
    }

    private void speak(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        Optional<ChatMemory.Line> line = ChatMemory.random(random, null);
        ServerPlayer source = players.get(random.nextInt(players.size()));
        boolean voiced = false;
        if (VoiceBridge.hasClip(source.getUUID())) {
            for (ServerPlayer sp : players) voiced |= VoiceBridge.play(sp, source.getUUID(), position().add(0, 3, 0));
        }
        if (!voiced) level().playSound(null, blockPosition(), ModSounds.get("entity.boss.voice"), SoundSource.HOSTILE, 3f, 1f);
        String name = line.map(ChatMemory.Line::name).orElse(source.getGameProfile().getName());
        String text = line.map(ChatMemory.Line::text).orElse(new String[]{
                "я — это вы", "не звони", "останься с нами", "тише... тише...", "вас было меньше", "кто из вас настоящий?"}[random.nextInt(6)]);
        for (ServerPlayer sp : players) {
            sp.sendSystemMessage(Component.translatable("chat.type.text",
                    Component.literal(name).withStyle(ChatFormatting.DARK_RED),
                    Component.literal(text).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)));
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!isStunned()) {
            if (!level().isClientSide && source.getEntity() instanceof ServerPlayer sp && hintCd <= 0) {
                hintCd = 100;
                sp.displayClientMessage(Component.literal("Удары проходят сквозь него. Оно слышит само себя. Нужен звон колокола.")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                ((ServerLevel) level()).sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2.5, getZ(), 8, 0.5, 1, 0.5, 0.02);
            }
            return false;
        }
        // every ring only cracks it so far: a full stun can take at most a sixth of its life
        float cap = getMaxHealth() * 0.17f;
        if (damageThisStun >= cap) return false;
        float dealt = Math.min(amount, cap - damageThisStun + 0.01f);
        boolean r = super.hurt(source, dealt);
        if (r) {
            damageThisStun += dealt;
            if (damageThisStun >= cap && !level().isClientSide) {
                stunTicks = Math.min(stunTicks, 10);
                for (ServerPlayer sp : arenaPlayers()) {
                    sp.displayClientMessage(Component.literal("Звон стих. Оно снова слышит себя — нужен другой колокол.")
                            .withStyle(ChatFormatting.GOLD), true);
                }
            }
        }
        return r;
    }

    @Override
    public boolean doHurtTarget(Entity e) {
        boolean r = super.doHurtTarget(e);
        if (r && e instanceof ServerPlayer sp) {
            Net.fx(sp, Fx.SHAKE, 15, 1.5f, "");
            Sanity.add(sp, -4f);
        }
        return r;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            bossEvent.removeAllPlayers();
            StoryManager.onBossDefeated((ServerLevel) level(), blockPosition());
        }
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level() instanceof ServerLevel sl && deathTime % 3 == 0) {
            sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2, getZ(), 12, 1, 1.5, 1, 0.08);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2, getZ(), 6, 1, 1.5, 1, 0.02);
        }
        if (deathTime >= 70 && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.get("entity.boss.idle");
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) {
        return ModSounds.get("entity.boss.hurt");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.get("entity.boss.death");
    }

    @Override
    protected float getSoundVolume() {
        return 3.0f;
    }

    @Override
    public boolean causeFallDamage(float d, float m, DamageSource s) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        tag.putBoolean("Scaled", scaled);
        ListTag l = new ListTag();
        for (BlockPos b : bells) l.add(NbtUtils.writeBlockPos(b));
        tag.put("Bells", l);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
        scaled = tag.getBoolean("Scaled");
        bells.clear();
        for (Tag t : tag.getList("Bells", Tag.TAG_COMPOUND)) bells.add(NbtUtils.readBlockPos((CompoundTag) t));
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }
}
