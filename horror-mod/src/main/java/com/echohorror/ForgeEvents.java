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
        if (ticks % 2 == 0) FlashlightManager.tick(server, ticks);
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
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            FlashlightManager.remove(sp.server, sp.getUUID());
            HorrorDirector.forget(sp.getUUID());
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
    public static void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        ChatMemory.record(p.getUUID(), p.getGameProfile().getName(), e.getRawText());
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
        if (d.chapter < StoryManager.CH_RELAY || d.chapter == StoryManager.CH_SILENCE) return;
        if (sp.getRandom().nextFloat() > 0.45f) return;
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
    public static void onCommands(RegisterCommandsEvent e) {
        EchoCommand.register(e.getDispatcher());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        FlashlightManager.clearAll(e.getServer());
        Scheduler.clear();
        ChatMemory.clear();
    }
}
