package com.echohorror.horror;

import com.echohorror.Config;
import com.echohorror.item.FlashlightItem;
import com.echohorror.network.Net;
import com.echohorror.network.StatePacket;
import com.echohorror.registry.ModItems;
import com.echohorror.story.StoryData;
import com.echohorror.story.StoryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Decides, per player, when the next scare happens and which one. Also drives sanity. */
public final class HorrorDirector {
    private static final class State {
        long nextEvent;
        final Map<String, Long> lastUse = new HashMap<>();
        int brokenTicks;
        int stalkStage;      // 0 = not stalked tonight
        long stalkNext;
        long stalkDay = -1;
        long awaySince = -1;
        long homeDay = -100;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Never during the boss fight: they would take control away at the worst moment. */
    private static final Set<String> DISRUPTIVE = Set.of("fake_death", "fake_disconnect", "fake_restart", "look_turn", "jumpscare", "fog", "rename_item", "crawlers", "mimic");

    private HorrorDirector() {}

    public static void forget(UUID id) {
        STATES.remove(id);
    }

    /** Called once per second. */
    public static void tick(MinecraftServer server, long seconds) {
        StoryData d = StoryData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            updateSanity(p, d);
            int flags = 0;
            if (p.level() == server.overworld() && StoryManager.echoNight(d, p.level())) flags |= StatePacket.FLAG_ECHO_NIGHT;
            if (d.in("depths", p.position()) || d.in("bunker", p.position()) || d.in("arena", p.position())) flags |= StatePacket.FLAG_DEEP;
            if (d.in("arena", p.position()) && d.flag("boss_spawned") && d.chapter == StoryManager.CH_BELFRY) flags |= StatePacket.FLAG_BOSS;
            Net.send(p, new StatePacket(Sanity.get(p), d.chapter, flags));

            if (d.chapter == StoryManager.CH_NONE || !HorrorUtil.isSurvivalLike(p) || !p.isAlive()) continue;
            if (seconds % 4 == 0 && Scares.horrorNearby(p, 10)) Net.fx(p, com.echohorror.network.Fx.HEARTBEAT, 100);
            State st = STATES.computeIfAbsent(p.getUUID(), k -> new State());
            if (st.nextEvent == 0) st.nextEvent = seconds + 20 + p.getRandom().nextInt(30);
            brokenMind(p, d, st);
            stalker(p, d, st, seconds);
            homecoming(p, d, st, seconds);
            if (seconds >= st.nextEvent) {
                runRandom(p, d, st, seconds);
                st.nextEvent = seconds + interval(p, d);
            }
        }
    }

    private static int interval(ServerPlayer p, StoryData d) {
        float sanity = Sanity.get(p);
        double base;
        int ch = StoryManager.effectiveChapter(d);
        if (ch == StoryManager.CH_NONE) base = 480;
        else if (ch == StoryManager.CH_SILENCE) base = 900;
        else {
            float fear = Mth.clamp(0.35f * ch / 6f + 0.65f * (1f - sanity / 100f), 0f, 1f);
            base = Mth.lerp(fear, 110, 25);
            if (StoryManager.echoNight(d, p.level())) base *= 0.6;
        }
        base *= 0.6 + p.getRandom().nextDouble() * 0.8;
        base /= Config.INTENSITY.get();
        return Math.max(12, (int) base);
    }

    private static void runRandom(ServerPlayer p, StoryData d, State st, long seconds) {
        Scares.Ctx c = Scares.ctx(p, d);
        List<Scares.Scare> pool = new ArrayList<>();
        int total = 0;
        boolean bossFight = d.chapter == StoryManager.CH_BELFRY && d.in("arena", p.position());
        // never take control away from someone driving, flying or fighting
        boolean busy = p.isPassenger() || p.isFallFlying() || p.tickCount - p.getLastHurtByMobTimestamp() < 200;
        bossFight |= busy;
        for (Scares.Scare s : Scares.all()) {
            if (c.chapter < s.minChapter() || c.sanity > s.maxSanity()) continue;
            if (bossFight && DISRUPTIVE.contains(s.name())) continue;
            if (c.chapter == StoryManager.CH_SILENCE && s.minChapter() > 1) continue;
            Long last = st.lastUse.get(s.name());
            if (last != null && seconds - last < s.cooldownSec()) continue;
            if (!s.cond().test(c)) continue;
            pool.add(s);
            total += s.weight();
        }
        for (int attempt = 0; attempt < 3 && total > 0; attempt++) {
            int roll = p.getRandom().nextInt(total);
            for (Scares.Scare s : pool) {
                roll -= s.weight();
                if (roll < 0) {
                    boolean ok;
                    try {
                        ok = s.run().apply(c);
                    } catch (Exception e) {
                        com.echohorror.EchoHorror.LOG.warn("Scare {} failed", s.name(), e);
                        ok = false;
                    }
                    if (ok) {
                        com.echohorror.EchoHorror.LOG.debug("Scare {} -> {}", s.name(), p.getGameProfile().getName());
                        st.lastUse.put(s.name(), seconds);
                        return;
                    }
                    break;
                }
            }
        }
    }

    /** Runs a named scare immediately (admin command). */
    public static boolean force(ServerPlayer p, String name) {
        Scares.Scare s = Scares.get(name);
        if (s == null) return false;
        Scares.Ctx c = Scares.ctx(p, StoryData.get(p.server));
        if (!s.cond().test(c)) return false;
        return s.run().apply(c);
    }

    /** Coming home after a long trip: sometimes the house was not empty. */
    private static void homecoming(ServerPlayer p, StoryData d, State st, long seconds) {
        BlockPos home = p.getRespawnPosition();
        if (home == null || p.getRespawnDimension() != p.level().dimension() || StoryManager.effectiveChapter(d) < StoryManager.CH_RELAY) return;
        double dist = Math.sqrt(home.distSqr(p.blockPosition()));
        if (dist > 100) {
            if (st.awaySince < 0) st.awaySince = seconds;
            return;
        }
        if (dist < 10 && st.awaySince >= 0) {
            long away = seconds - st.awaySince;
            st.awaySince = -1;
            long day = p.level().getDayTime() / 24000;
            if (away >= 300 && day - st.homeDay >= 2 && p.getRandom().nextFloat() < 0.5f && force(p, "homecoming")) st.homeDay = day;
        }
    }

    private static final double[] STALK_DIST = {46, 36, 27, 19, 12};

    /**
     * Преследователь: once a night something picks one player and comes a little closer every time it is seen.
     * The last time it is right behind you.
     */
    private static void stalker(ServerPlayer p, StoryData d, State st, long seconds) {
        if ((d.chapter < StoryManager.CH_RELAY || d.chapter >= StoryManager.CH_BELFRY) && !d.flag("ending_echo")) return;
        boolean night = HorrorUtil.isNight(p.serverLevel());
        long day = p.serverLevel().getDayTime() / 24000L;
        if (!night) {
            st.stalkStage = 0;
            return;
        }
        if (st.stalkStage == 0) {
            if (st.stalkDay == day) return;
            st.stalkDay = day;
            float chance = 0.35f + 0.5f * Sanity.fear(p);
            if (p.getRandom().nextFloat() > chance) return;
            st.stalkStage = 1;
            st.stalkNext = seconds + 40 + p.getRandom().nextInt(60);
            return;
        }
        if (seconds < st.stalkNext || st.stalkStage > STALK_DIST.length + 1) return;
        st.stalkNext = seconds + 50 + p.getRandom().nextInt(50);
        if (st.stalkStage <= STALK_DIST.length) {
            double dist = STALK_DIST[st.stalkStage - 1];
            boolean placed = HorrorUtil.spotAround(p, dist - 3, dist + 3, 0, 75, false).map(s -> {
                com.echohorror.entity.PhantomEntity.spawn(p, com.echohorror.entity.PhantomEntity.KIND_WATCHER,
                        com.echohorror.entity.PhantomEntity.MODE_STARE, s, 500).vanishDistance(Math.max(4, dist - 8)).watchLimit(25);
                return true;
            }).orElse(false);
            if (placed) {
                if (st.stalkStage >= 3) HorrorUtil.playAt(p, "scare.heartbeat", 0.6f, 1f);
                st.stalkStage++;
            }
        } else {
            // it has arrived
            Vec3 b = HorrorUtil.behind(p, 1.8);
            HorrorUtil.ground(p.serverLevel(), Mth.floor(b.x), Mth.floor(b.z), p.getBlockY() + 1, 2, 3).ifPresent(g -> {
                com.echohorror.entity.PhantomEntity.spawn(p, com.echohorror.entity.PhantomEntity.KIND_WATCHER,
                        com.echohorror.entity.PhantomEntity.MODE_BEHIND, Vec3.atBottomCenterOf(g), 300).withJumpscare();
                HorrorUtil.playTo(p, "scare.breath", Vec3.atCenterOf(g).add(0, 1.5, 0), 1f, 0.7f);
                com.echohorror.story.Achievements.award(p, "stalker");
            });
            st.stalkStage++;
        }
    }

    private static void brokenMind(ServerPlayer p, StoryData d, State st) {
        if (Sanity.get(p) > 0.5f || d.chapter < StoryManager.CH_RELAY || d.chapter == StoryManager.CH_SILENCE) {
            st.brokenTicks = 0;
            return;
        }
        if (st.brokenTicks == 0) com.echohorror.story.Achievements.award(p, "broken");
        if (++st.brokenTicks % 25 == 0) {
            p.displayClientMessage(Component.literal("Ты слышишь, как кто-то повторяет твои мысли. Прими таблетки.")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
            HorrorUtil.playTo(p, "whisper", HorrorUtil.behind(p, 0.6), 1f, 0.7f);
            if (p.getHealth() > 6f) p.hurt(p.damageSources().magic(), 1f);
        }
    }

    private static void updateSanity(ServerPlayer p, StoryData d) {
        if (!HorrorUtil.isSurvivalLike(p) || !p.isAlive()) return;
        if (d.chapter == StoryManager.CH_NONE) {
            Sanity.add(p, 0.2f);
            return;
        }
        BlockPos eye = BlockPos.containing(p.getEyePosition());
        int light = p.level().getMaxLocalRawBrightness(eye);
        boolean night = HorrorUtil.isNight(p.serverLevel());
        float delta = 0f;
        if (light <= 3) delta -= 0.22f;
        else if (light <= 7) delta -= 0.10f;
        else if (light >= 12) delta += 0.10f;
        if (night && p.level().canSeeSky(eye)) delta -= 0.04f;
        if (HorrorUtil.isUnderground(p)) delta -= 0.04f;
        if (d.in("depths", p.position()) || d.in("bunker", p.position()) || d.in("arena", p.position())) delta -= 0.08f;
        if (StoryManager.echoNight(d, p.level())) delta -= 0.08f;
        if (Scares.horrorNearby(p, 12)) delta -= 0.15f;

        int online = p.server.getPlayerCount();
        boolean company = false, alone = true;
        for (ServerPlayer o : HorrorUtil.others(p)) {
            if (o.level() != p.level()) continue;
            double dd = o.distanceToSqr(p);
            if (dd < 8 * 8) company = true;
            if (dd < 24 * 24) alone = false;
        }
        if (company) delta += 0.12f;
        else if (online > 1 && alone) delta -= 0.06f;
        if (holdingLitFlashlight(p)) delta += 0.06f;

        if (delta < 0) delta *= (0.6f + 0.12f * d.chapter) * Config.SANITY_DRAIN.get().floatValue();
        boolean silence = p.getInventory().contains(new ItemStack(ModItems.SILENCE.get()));
        if (silence) delta = Math.max(delta, 0.3f);
        if (StoryManager.effectiveChapter(d) == StoryManager.CH_SILENCE) delta = Math.max(delta, 0.15f);
        Sanity.add(p, delta);
        if (silence && Sanity.get(p) < 60) Sanity.set(p, 60);
    }

    private static boolean holdingLitFlashlight(ServerPlayer p) {
        ItemStack m = p.getMainHandItem(), o = p.getOffhandItem();
        return (m.getItem() instanceof FlashlightItem && FlashlightItem.isOn(m)) || (o.getItem() instanceof FlashlightItem && FlashlightItem.isOn(o));
    }
}
