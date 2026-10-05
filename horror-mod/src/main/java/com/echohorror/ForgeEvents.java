package com.echohorror;

import com.echohorror.command.EchoCommand;
import com.echohorror.entity.PhantomEntity;
import com.echohorror.horror.*;
import com.echohorror.network.Fx;
import com.echohorror.network.Net;
import com.echohorror.story.StoryData;
import com.echohorror.story.StoryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EchoHorror.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEvents {
    private static long ticks;

    private ForgeEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = e.getServer();
        ticks++;
        Scheduler.tick();
        if (ticks % 2 == 0) {
            FlashlightManager.tick(server, ticks);
            PathMemory.tick(server);
            if (ticks % 10 == 0) MusicBoxAura.tick(server);
        }
        if (ticks % 20 == 0) {
            try {
                StoryManager.tick(server);
            } catch (Exception ex) {
                EchoHorror.LOG.error("Story tick failed", ex);
            }
            try {
                HorrorDirector.tick(server, ticks / 20);
            } catch (Exception ex) {
                EchoHorror.LOG.error("Horror tick failed", ex);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            StoryManager.giveKit(sp);
            StoryManager.sendJournal(sp, false);
            if (!Config.AUTO_START.get() && StoryData.get(sp.server).chapter == StoryManager.CH_NONE && sp.hasPermissions(2)) {
                sp.sendSystemMessage(Component.literal("[ЭХО] Сюжет ещё не начат. Чтобы начать: /echo start")
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            FlashlightManager.remove(sp.server, sp.getUUID());
            HorrorDirector.forget(sp.getUUID());
            PathMemory.forget(sp.getUUID());
            Encounters.forget(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData().getCompound("echohorror");
        if (!old.isEmpty()) e.getEntity().getPersistentData().put("echohorror", old.copy());
        if (e.isWasDeath() && e.getEntity() instanceof ServerPlayer sp) {
            Sanity.set(sp, Math.max(50f, Sanity.get(sp)));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            StoryManager.sendJournal(sp, false);
            if (StoryData.get(sp.server).chapter >= StoryManager.CH_VILLAGE) {
                sp.sendSystemMessage(Component.literal("Ты очнулся. Ты уверен, что это ты?").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) FlashlightManager.remove(sp.server, sp.getUUID());
    }

    @SubscribeEvent
    public static void onEntityJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide && e.getEntity() instanceof net.minecraft.world.entity.Mob m && m.getTags().contains("echohorror_stare")) {
            m.setNoAi(false);
            m.setSilent(false);
            m.removeTag("echohorror_stare");
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        ChatMemory.record(p.getUUID(), p.getGameProfile().getName(), e.getRawText());
        // the echo: sometimes your own words come back to you a few seconds later
        String text = e.getRawText();
        if (text != null && com.echohorror.horror.ChatReplies.maybeReply(p, text)) return;
        if (text != null && !text.startsWith("/") && StoryData.get(p.server).chapter >= StoryManager.CH_RELAY
                && StoryData.get(p.server).chapter < StoryManager.CH_SILENCE && Config.FAKE_CHAT.get() && p.getRandom().nextFloat() < 0.1f) {
            String echo = text.toLowerCase(java.util.Locale.ROOT).replaceAll("[!?.]+$", "") + "...";
            Scheduler.schedule(60 + p.getRandom().nextInt(100), () -> {
                if (!p.hasDisconnected()) p.sendSystemMessage(Component.translatable("chat.type.text",
                        p.getGameProfile().getName(), Component.literal(echo).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            });
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof ServerPlayer sp)) return;
        if (e.getLevel().getBlockState(e.getPos()).getBlock() instanceof BellBlock) {
            if (StoryManager.onBellUse(sp, e.getPos())) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
        // sometimes, at night, a door you just opened is pushed shut from the other side
        net.minecraft.world.level.block.state.BlockState st = e.getLevel().getBlockState(e.getPos());
        if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door && !st.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)
                && !st.is(net.minecraft.world.level.block.Blocks.IRON_DOOR) && Config.WORLD_TAMPERING.get()
                && HorrorUtil.isNight(sp.serverLevel()) && StoryManager.effectiveChapter(StoryData.get(sp.server)) >= StoryManager.CH_VILLAGE
                && StoryManager.effectiveChapter(StoryData.get(sp.server)) != StoryManager.CH_SILENCE && sp.getRandom().nextFloat() < 0.05f) {
            net.minecraft.core.BlockPos pos = e.getPos().immutable();
            Scheduler.schedule(12, () -> {
                net.minecraft.world.level.block.state.BlockState now = sp.serverLevel().getBlockState(pos);
                if (!(now.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) || !now.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) return;
                door.setOpen(null, sp.serverLevel(), now, pos, false);
                sp.serverLevel().playSound(null, pos, net.minecraft.sounds.SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, net.minecraft.sounds.SoundSource.BLOCKS, 0.9f, 1.2f);
                Sanity.add(sp, -3);
            });
        }
    }

    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        StoryData d = StoryData.get(sp.server);
        if (d.chapter < StoryManager.CH_SIGNAL || d.chapter == StoryManager.CH_SILENCE) return;
        float chance = 0.25f + 0.4f * Sanity.fear(sp);
        if (sp.getRandom().nextFloat() < chance) {
            String[] why = {"Ты не можешь уснуть: кто-то дышит под кроватью.", "Ты закрываешь глаза — и слышишь, как кто-то повторяет твоё дыхание.",
                    "Сон не идёт. В углу комнаты кто-то стоит. Или нет?", "Кто-то тихо зовёт тебя по имени. Уснуть невозможно."};
            e.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            sp.displayClientMessage(Component.literal(why[sp.getRandom().nextInt(why.length)]).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
            HorrorUtil.playTo(sp, "scare.breath", sp.position().add(0, -0.5, 0), 0.8f, 0.8f);
            Sanity.add(sp, -3);
        }
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.wakeImmediately()) return;
        StoryData d = StoryData.get(sp.server);
        if (d.chapter < StoryManager.CH_SIGNAL || d.chapter == StoryManager.CH_SILENCE) return;
        if (sp.getRandom().nextFloat() < 0.6f && com.echohorror.story.Dreams.tryDream(sp, d)) return;
        if (d.chapter < StoryManager.CH_RELAY || sp.getRandom().nextFloat() > 0.45f) return;
        // sleep paralysis
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
        sp.displayClientMessage(Component.literal("Ты не можешь пошевелиться.").withStyle(ChatFormatting.DARK_RED), true);
        Scheduler.schedule(30, () -> {
            if (sp.hasDisconnected()) return;
            Vec3 look = sp.getLookAngle();
            Vec3 spot = sp.position().add(look.x * 2, 0, look.z * 2);
            HorrorUtil.ground(sp.serverLevel(), Mth.floor(spot.x), Mth.floor(spot.z), sp.getBlockY() + 1, 2, 3)
                    .ifPresent(g -> PhantomEntity.spawn(sp, PhantomEntity.KIND_WATCHER, PhantomEntity.MODE_STILL, Vec3.atBottomCenterOf(g), 70));
            HorrorUtil.playTo(sp, "whisper", sp.getEyePosition().add(look), 1f, 0.7f);
            Net.fx(sp, Fx.HEARTBEAT, 100);
            Sanity.add(sp, -10);
        });
    }

    @SubscribeEvent
    public static void onGameEvent(net.minecraftforge.event.VanillaGameEvent e) {
        if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            NoiseSystem.onGameEvent(sl, e.getVanillaEvent(), e.getEventPosition(), e.getCause());
        }
    }

    @SubscribeEvent
    public static void onLoudSound(net.minecraftforge.event.PlayLevelSoundEvent.AtPosition e) {
        if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            NoiseSystem.onSound(sl, e.getPosition(), e.getNewVolume(), e.getSource());
        }
    }

    private static final String[] PROTECTED = {"bunker", "arena", "stairs", "gatewall"};

    private static boolean isProtected(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl) || sl.dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        StoryData d = StoryData.get(sl.getServer());
        if (d.chapter == StoryManager.CH_NONE) return false;
        Vec3 c = Vec3.atCenterOf(pos);
        for (String r : PROTECTED) if (d.in(r, c)) return true;
        return false;
    }

    /** The Object and the Belfry cannot be dug around: the story has to be walked, not tunnelled. */
    @SubscribeEvent
    public static void onBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent e) {
        if (e.getPlayer() == null || e.getPlayer().isCreative()) return;
        if (e.getLevel() instanceof net.minecraft.world.level.Level lvl && isProtected(lvl, e.getPos())) {
            e.setCanceled(true);
            e.getPlayer().displayClientMessage(Component.literal("Стены объекта не поддаются. Будто кто-то держит их изнутри.")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(net.minecraftforge.event.level.ExplosionEvent.Detonate e) {
        e.getAffectedBlocks().removeIf(pos -> isProtected(e.getLevel(), pos));
    }

    /** The dead do not stay dead: a little later, something wearing your face waits where you fell. */
    @SubscribeEvent
    public static void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !Config.HOSTILE_SPAWNS.get()) return;
        StoryData d = StoryData.get(sp.server);
        int ch = StoryManager.effectiveChapter(d);
        if (ch < StoryManager.CH_VILLAGE || ch > StoryManager.CH_BELFRY) return;
        net.minecraft.server.level.ServerLevel level = sp.serverLevel();
        Vec3 where = sp.position();
        java.util.UUID id = sp.getUUID();
        String name = sp.getGameProfile().getName();
        Scheduler.schedule(1200 + sp.getRandom().nextInt(1200), () -> {
            if (!HorrorUtil.isNight(level) || d.in("arena", where)) return;
            com.echohorror.entity.MimicEntity m = new com.echohorror.entity.MimicEntity(com.echohorror.registry.ModEntities.MIMIC.get(), level);
            m.moveTo(where.x, where.y, where.z, sp.getRandom().nextFloat() * 360f, 0f);
            m.disguise(id, name);
            level.addFreshEntity(m);
        });
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        EchoCommand.register(e.getDispatcher());
    }

    @SubscribeEvent
    public static void onStarted(net.minecraftforge.event.server.ServerStartedEvent e) {
        com.echohorror.compat.TaczCompat.filterRecipes(e.getServer());
    }

    @SubscribeEvent
    public static void onDatapackSync(net.minecraftforge.event.OnDatapackSyncEvent e) {
        if (e.getPlayer() == null) com.echohorror.compat.TaczCompat.filterRecipes(e.getPlayerList().getServer()); // after /reload
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        FlashlightManager.clearAll(e.getServer());
        StoryManager.onServerStopping();
        Scheduler.clear();
        ChatMemory.clear();
        PathMemory.clear();
        VoiceNoise.clear();
        MusicBoxAura.clear();
    }
}
