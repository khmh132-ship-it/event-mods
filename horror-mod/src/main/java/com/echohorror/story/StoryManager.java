package com.echohorror.story;

import com.echohorror.Config;
import com.echohorror.EchoHorror;
import com.echohorror.entity.CrawlerEntity;
import com.echohorror.entity.EchoBossEntity;
import com.echohorror.entity.MimicEntity;
import com.echohorror.entity.PhantomEntity;
import com.echohorror.horror.HorrorUtil;
import com.echohorror.horror.MusicBoxAura;
import com.echohorror.horror.Sanity;
import com.echohorror.horror.Scheduler;
import com.echohorror.item.NoteItem;
import com.echohorror.network.*;
import com.echohorror.registry.ModEntities;
import com.echohorror.registry.ModItems;
import com.echohorror.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The story director: chapters, triggers, broadcasts and set pieces. Server side only. */
public final class StoryManager {
    public static final int CH_NONE = 0, CH_SIGNAL = 1, CH_RELAY = 2, CH_VILLAGE = 3, CH_DEPTHS = 4, CH_OBJECT = 5, CH_BELFRY = 6, CH_SILENCE = 7;

    private static int firstPlayerTicks;
    /** Chapter changes waiting in the (in-memory) scheduler; if the server stops meanwhile, recover() finishes them. */
    private static int pendingTransitions;
    private static boolean bossRespawnPending;

    private static void beginTransition() {
        pendingTransitions++;
    }

    private static void endTransition() {
        pendingTransitions = Math.max(0, pendingTransitions - 1);
    }

    private static boolean campsPending;
    private static boolean pioneerPending;

    public static void onServerStopping() {
        pendingTransitions = 0;
        bossRespawnPending = false;
        campsPending = false;
        pioneerPending = false;
        blackoutActive = false;
        firstPlayerTicks = 0;
    }

    private StoryManager() {}

    public static StoryData data(MinecraftServer server) {
        return StoryData.get(server);
    }

    public static String chapterTitle(int ch) {
        return switch (ch) {
            case CH_SIGNAL -> "ПРОЛОГ|Сигнал";
            case CH_RELAY -> "ГЛАВА I|Ретранслятор";
            case CH_VILLAGE -> "ГЛАВА II|Тихий Лог";
            case CH_DEPTHS -> "ГЛАВА III|Глубина";
            case CH_OBJECT -> "ГЛАВА IV|Объект «Колокол»";
            case CH_BELFRY -> "ГЛАВА V|Звонница";
            case CH_SILENCE -> "ЭПИЛОГ|Конец эфира";
            default -> "|";
        };
    }

    public static String objective(StoryData d) {
        return switch (d.chapter) {
            case CH_NONE -> "Всё спокойно. Слишком спокойно. Дождитесь ночи.";
            case CH_SIGNAL -> "Найдите источник сигнала — ретранслятор Р-7.\nДержите пеленгатор в руке: он покажет направление.";
            case CH_RELAY -> d.flag("console_used") ? "Слушайте эфир."
                    : "Изучите ретранслятор. Прочтите журнал радиста.\nВключите передатчик на столе — НОЧЬЮ.";
            case CH_VILLAGE -> !d.flag("clapper_installed")
                    ? "Тихий Лог. Узнайте, что случилось с жителями.\nКолокол церкви нем: найдите его язык. Говорят, его унесли в подпол."
                    : "Язык колокола на месте.\nПоднимитесь на колокольню и позвоните в колокол в ПОЛНОЧЬ.";
            case CH_DEPTHS -> "Колодец открыт. Спуститесь в Глубину.\nНайдите три осколка колокола и вставьте их в печать у ворот объекта ("
                    + lockCount(d) + "/3).\nПолзуны слепы: двигайтесь пригнувшись.";
            case CH_OBJECT -> "Объект «Колокол». Восстановите питание: три рубильника (" + d.switchesOn.size() + "/3).\n"
                    + "НЕ ОТВОДИТЕ ВЗГЛЯД от Немой. Работайте парами.";
            case CH_BELFRY -> d.flag("boss_dead")
                    ? "Отголосок рассыпался. Выбор за вами.\nДёрнуть верёвку Великого колокола в центре зала — или поднять сердце Эха и послушать."
                    : d.flag("boss_spawned") ? "Звоните в колокола, чтобы оглушить Отголосок. Пока он оглушён — бейте.\nОдин колокол дважды подряд не сработает."
                    : "Спуститесь в Звонницу. Заставьте Эхо замолчать.";
            case CH_SILENCE -> d.flag("ending_echo") ? "Вы выбрали слушать.\nЭхо теперь говорит вашими голосами. Ночи больше не кончаются."
                    : "Тишина.\nСюжет завершён. Но эхо иногда возвращается.";
            default -> "";
        };
    }

    private static int lockCount(StoryData d) {
        return d.flag("lock3") ? 3 : d.flag("lock2") ? 2 : d.flag("lock1") ? 1 : 0;
    }

    // =========================================================================================== sync
    public static void sendJournal(ServerPlayer p, boolean open) {
        StoryData d = data(p.server);
        Net.send(p, new JournalPacket(open, d.chapter, objective(d), new ArrayList<>(d.notes)));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendJournal(p, false);
    }

    private static void broadcast(MinecraftServer server, Component c) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(c);
    }

    private static void broadcastBar(MinecraftServer server, Component c) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.displayClientMessage(c, true);
    }

    public static void setChapter(MinecraftServer server, int ch) {
        StoryData d = data(server);
        if (d.chapter == ch) return;
        d.chapter = ch;
        d.setDirty();
        EchoHorror.LOG.info("Story chapter -> {}", ch);
        String adv = switch (ch) {
            case CH_SIGNAL -> "root";
            case CH_RELAY -> "signal";
            case CH_VILLAGE -> "broadcast";
            case CH_DEPTHS -> "midnight";
            case CH_OBJECT -> "sealed";
            case CH_BELFRY -> "power";
            default -> null;
        };
        if (adv != null) Achievements.awardAll(server, adv);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Net.fx(p, Fx.CHAPTER, chapterTitle(ch));
            HorrorUtil.playAt(p, "story.chapter", 0.9f, 1f);
            giveKit(p);
        }
        syncAll(server);
    }

    // =========================================================================================== kit
    public static void giveKit(ServerPlayer p) {
        StoryData d = data(p.server);
        if (d.chapter >= CH_SIGNAL) Achievements.award(p, "root");
        if (d.chapter < CH_SIGNAL || d.kits.contains(p.getUUID())) return;
        d.kits.add(p.getUUID());
        d.setDirty();
        give(p, new ItemStack(ModItems.JOURNAL.get()));
        give(p, new ItemStack(ModItems.LOCATOR.get()));
        give(p, new ItemStack(ModItems.FLASHLIGHT.get()));
        give(p, new ItemStack(ModItems.BATTERY.get(), 2));
        give(p, new ItemStack(ModItems.PILLS.get(), 1));
        p.sendSystemMessage(Component.literal("В рюкзаке: полевой дневник, пеленгатор, фонарь. Держитесь вместе.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    // =========================================================================================== start
    public static void start(MinecraftServer server, BlockPos origin) {
        StoryData d = data(server);
        ServerLevel level = server.overworld();
        d.put("origin", origin);
        BlockPos site = Structures.findSite(level, origin, Config.LOCATION_DISTANCE.get(), 12);
        Structures.buildRadio(level, site, d);
        d.notes.add("prologue");
        setChapter(server, CH_SIGNAL);
        int count = server.getPlayerList().getPlayerCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Net.send(p, prologueBroadcast(count));
        }
        // the first night does not wait: something is already watching when the broadcast ends
        Scheduler.schedule(880, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!HorrorUtil.isSurvivalLike(p)) continue;
                com.echohorror.horror.HorrorDirector.force(p, "watcher");
                HorrorUtil.playTo(p, "whisper", HorrorUtil.behind(p, 1.2), 0.8f, 0.85f);
            }
        });
        Scheduler.schedule(1500, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (HorrorUtil.isSurvivalLike(p)) com.echohorror.horror.HorrorDirector.force(p, "footsteps");
            }
        });
    }

    private static SoundSeqPacket prologueBroadcast(int count) {
        SoundSeqPacket.Builder b = new SoundSeqPacket.Builder()
                .sound("radio.tune", 60, "[ треск помех ]")
                .sound("scare.static", 30)
                .sound("radio.b_prologue", 50, "«Всем, кто слышит. Не отвечайте голосам из темноты.»")
                .sub("«Повторяю. Не отвечайте голосам из темноты.»", 120)
                .sub("«Не открывайте дверь, если стучат ночью.»", 110);
        appendCount(b, count, false, 140);
        return b.build();
    }

    /** Numbers-station count. */
    private static void appendCount(SoundSeqPacket.Builder b, int count, boolean more, int firstDelay) {
        b.sound("radio.intro", firstDelay, "[ позывной ]");
        b.sound("radio.b_attention", 170, "«Внимание. Внимание. Говорит ретранслятор Р-7. Считаю.»");
        int n = Mth.clamp(count, 1, 12);
        String[] words = {"ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять", "десять", "одиннадцать", "двенадцать"};
        int delay = 175;
        for (int i = 1; i <= n; i++) {
            b.sound("radio.num" + i, delay, "«" + words[i] + "...»");
            delay = 34;
        }
        if (more) {
            b.sound("radio.num" + Math.min(12, n + 1), 60, "«...» " + words[Math.min(12, n + 1)] + ".");
            b.sound("radio.b_more", 50, "«Вас стало больше.»");
            b.sound("radio.b_end", 80, "«Конец связи.»");
        } else {
            b.sound("radio.b_end", 50, "«Конец связи.»");
        }
    }

    // =========================================================================================== tick
    public static void tick(MinecraftServer server) {
        StoryData d = data(server);
        ServerLevel ow = server.overworld();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        if (d.chapter == CH_NONE) {
            firstPlayerTicks += 20;
            if (!Config.AUTO_START.get() && firstPlayerTicks % 6000 == 20) dormantHint(server);
            if (Config.AUTO_START.get() && firstPlayerTicks >= 600) {
                ServerPlayer first = players.get(0);
                // the sun goes down faster than it should
                long t = ow.getDayTime() % 24000L;
                if (t < 11500 || t > 23500) {
                    long base = ow.getDayTime() - t;
                    ow.setDayTime(base + (t > 23500 ? 24000L : 0L) + 11500L);
                }
                for (ServerPlayer p : players) {
                    p.sendSystemMessage(Component.literal("Солнце садится быстрее, чем должно.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                }
                start(server, first.level() == ow ? first.blockPosition() : ow.getSharedSpawnPos());
            }
            return;
        }
        for (ServerPlayer p : players) giveKit(p);
        if (pendingTransitions == 0) recover(server, d);

        // nightly count at midnight
        long day = ow.getDayTime() / 24000L;
        if (d.chapter >= CH_RELAY && effectiveChapter(d) < CH_SILENCE && HorrorUtil.isMidnight(ow) && d.lastCountDay != day) {
            d.lastCountDay = day;
            d.nights++;
            if (d.nights % 3 == 0) d.echoDay = day;
            d.setDirty();
            int count = players.size();
            for (ServerPlayer p : players) {
                if (!hasLocator(p)) continue;
                SoundSeqPacket.Builder b = new SoundSeqPacket.Builder().sound("radio.tune", 1, "[ пеленгатор оживает ]");
                if (d.nights % 2 == 0) b.sound("radio.b_counting", 40, "«Я считаю вас каждую ночь. И каждую ночь вас на одного больше.»");
                appendCount(b, count, d.chapter >= CH_VILLAGE, d.nights % 2 == 0 ? 190 : 40);
                Net.send(p, b.build());
            }
            if (d.chapter >= CH_VILLAGE && echoNight(d, ow)) {
                for (ServerPlayer p : players) {
                    p.sendSystemMessage(Component.literal("Ночь Эха. Небо слушает.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
                    HorrorUtil.playAt(p, "story.bell_far", 0.8f, 0.8f);
                }
                if (effectiveChapter(d) >= CH_DEPTHS && (!d.flag("ending_echo") || d.nights % 2 == 0)) siege(server, d);
                ow.setWeatherParameters(0, 5200, true, true); // the sky joins in
            }
        }

        if (d.chapter >= CH_RELAY && !campsPending && d.get("camp2") == null) buildCamps(server, d);
        if (d.flag("blackout") && !d.flag("blackout_end") && !blackoutActive) endBlackout(ow, d);
        if (d.chapter >= CH_VILLAGE && !pioneerPending && d.get("pioneer") == null && d.get("village") != null) {
            pioneerPending = true;
            Scheduler.schedule(100, () -> {
                pioneerPending = false;
                if (d.get("pioneer") != null) return;
                BlockPos v = d.get("village");
                List<BlockPos> avoid = new ArrayList<>(d.pos.values());
                BlockPos site = Structures.findSite(ow, v, 150, 18, ow.random.nextDouble() * Math.PI * 2, avoid);
                Structures.buildPioneerCamp(ow, site, d);
            });
        }

        for (ServerPlayer p : players) {
            if (p.level() != ow || p.isSpectator()) continue;
            Vec3 pos = p.position();
            switch (d.chapter) {
                case CH_SIGNAL -> {
                    BlockPos r = d.get("radio");
                    if (r != null && pos.distanceTo(Vec3.atCenterOf(r)) < 18) {
                        setChapter(server, CH_RELAY);
                        HorrorUtil.playAt(p, "scare.static", 0.6f, 1f);
                    }
                }
                case CH_VILLAGE -> {
                    if (d.in("cellar", pos) && d.setFlag("cellar_scare")) cellarScare(p, d);
                }
                case CH_DEPTHS -> {
                    for (int i = 0; i < 3; i++) {
                        if (d.in("shard" + i, pos) && d.setFlag("ambush_shard" + i)) ambush(p, 2 + players.size() / 2);
                    }
                }
                case CH_OBJECT -> {
                    if (d.in("archive", pos) && d.setFlag("archive_mimic")) archiveMimic(p, d);
                    if (d.in("genroom", pos) && d.setFlag("gen_ambush")) ambush(p, 3 + players.size() / 2);
                }
                case CH_BELFRY -> {
                    if (d.in("arena", pos)) belfryTick(server, p, d);
                }
                default -> {}
            }
            locationAmbience(p, d);
            pioneerAmbience(p, d);
            for (int k = 0; k < 3; k++) {
                if (d.in("camp" + k, pos) && d.setFlag("camp_seen" + k)) campScene(p, k);
            }
            if (d.in("pioneer", pos)) {
                if (d.setFlag("pioneer_seen")) {
                    Net.fx(p, Fx.SUBTITLE, 100, 0, "Пионерлагерь «Звёздочка». Вторая смена, 1986. Качели ещё качаются.");
                    Scheduler.schedule(80, () -> HorrorUtil.playTo(p, "voice.laugh", HorrorUtil.behind(p, 14), 0.8f, 1.35f));
                }
                if (!d.flag("lineup") && p.getInventory().contains(new ItemStack(ModItems.MUSIC_BOX.get())) && d.setFlag("lineup")) {
                    lineup(server, d);
                }
            }
            // one-time location titles
            if (d.in("village", pos) && d.setFlag("title_village")) {
                Net.fx(p, Fx.SUBTITLE, 100, 0, "Тихий Лог. Ни одного огня. Ни одной собаки.");
                lisitsynLure(server, d);
                spawnKuzmich(server, d);
            }
            if (d.in("depths", pos)) Achievements.award(p, "depths");
            if (d.in("depths", pos) && d.setFlag("title_depths")) {
                Net.fx(p, Fx.SUBTITLE, 100, 0, "Стены тёплые. Где-то внизу кто-то повторяет ваши шаги.");
            }
            if (d.in("bunker", pos) && d.setFlag("title_bunker")) {
                Net.fx(p, Fx.SUBTITLE, 100, 0, "Объект «Колокол». Здесь давно никто не говорил вслух.");
            }
        }
    }

    /** Tells operators that the mod is waiting for /echo start. */
    public static void dormantHint(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2)) {
                p.sendSystemMessage(Component.literal("[ЭХО] Сюжет ещё не начат. Чтобы начать: /echo start (точка старта — там, где вы стоите).")
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    /** Echo night siege: three waves of the blind and the borrowed come out of the dark around every player. */
    public static void siege(MinecraftServer server, StoryData d) {
        if (!Config.HOSTILE_SPAWNS.get()) return;
        for (int wave = 0; wave < 3; wave++) {
            final int w = wave;
            Scheduler.schedule(300 + wave * 1300, () -> {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.level() != server.overworld() || !HorrorUtil.isSurvivalLike(p) || !p.isAlive()) continue;
                    if (d.in("depths", p.position()) || d.in("bunker", p.position()) || d.in("arena", p.position())) continue;
                    if (!HorrorUtil.isNight(p.serverLevel())) continue;
                    ServerLevel level = p.serverLevel();
                    int crawlers = 2 + w;
                    for (int i = 0; i < crawlers; i++) {
                        HorrorUtil.spotAround(p, 18, 30, p.getRandom().nextInt(360), 30, true).ifPresent(s -> {
                            CrawlerEntity c = new CrawlerEntity(ModEntities.CRAWLER.get(), level);
                            c.moveTo(s.x, s.y, s.z, level.random.nextFloat() * 360, 0);
                            c.setTarget(p);
                            level.addFreshEntity(c);
                        });
                    }
                    if (w == 2) {
                        List<ServerPlayer> faces = server.getPlayerList().getPlayers();
                        HorrorUtil.spotAround(p, 20, 30, 180, 90, true).ifPresent(s ->
                                MimicEntity.spawnAs(level, s, faces.get(level.random.nextInt(faces.size()))));
                    }
                    if (w == 0) Net.fx(p, Fx.SUBTITLE, 80, 0, "Из темноты что-то идёт. Много.");
                    HorrorUtil.playTo(p, "entity.crawler.click", HorrorUtil.behind(p, 14), 2f, 0.8f);
                }
            });
        }
    }

    private static void campScene(ServerPlayer p, int kind) {
        String[] titles = {"Лагерь геологической партии №4. Палатка ещё тёплая.", "Пост оцепления. Ни одного солдата.",
                "Машина. Ключ в зажигании. На сиденье — женская сумка."};
        Net.fx(p, Fx.SUBTITLE, 100, 0, titles[kind]);
        switch (kind) {
            case 0 -> Scheduler.schedule(60, () -> HorrorUtil.playTo(p, "voice.call", HorrorUtil.behind(p, 12), 1.2f, 0.9f));
            case 1 -> Scheduler.schedule(80, () -> {
                HorrorUtil.playTo(p, net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, HorrorUtil.behind(p, 20), 1.2f, 0.8f);
                Scheduler.schedule(10, () -> p.displayClientMessage(Component.literal("«Стой! Кто идёт?» — где-то в лесу.")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true));
            });
            default -> Scheduler.schedule(60, () -> HorrorUtil.playTo(p, "scare.knock", p.position().add(1, 1, 0), 1f, 1.1f));
        }
    }

    /** «Всем отрядам — на линейку». Someone took Masha's music box out of the medical hut. */
    private static void lineup(MinecraftServer server, StoryData d) {
        ServerLevel level = server.overworld();
        BlockPos speaker = d.get("pioneer_speaker");
        BlockPos flag = d.get("pioneer");
        if (speaker == null || flag == null) return;
        Vec3 sp = Vec3.atCenterOf(speaker), center = Vec3.atBottomCenterOf(flag);
        java.util.function.Consumer<java.util.function.Consumer<ServerPlayer>> near = act -> {
            for (ServerPlayer p : level.players())
                if (!p.isSpectator() && p.position().distanceTo(center) < 90) act.accept(p);
        };
        near.accept(p -> {
            HorrorUtil.playTo(p, "story.camp_horn", sp, 4f, 1f);
            Net.fx(p, Fx.FLICKER, 30);
        });
        Scheduler.schedule(170, () -> near.accept(p -> HorrorUtil.playTo(p, "voice.camp_lineup", sp, 4f, 1f)));
        Scheduler.schedule(260, () -> near.accept(p -> {
            // thirty-something children in a ring around the flagpole, facing each other
            int n = 16;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                int x = Mth.floor(center.x + Math.cos(a) * 5), z = Mth.floor(center.z + Math.sin(a) * 5);
                java.util.Optional<BlockPos> g = HorrorUtil.ground(level, x, z, flag.getY() + 1, 3, 4);
                if (g.isEmpty()) continue;
                PhantomEntity.spawn(p, PhantomEntity.KIND_SHADE, PhantomEntity.MODE_CIRCLE, Vec3.atBottomCenterOf(g.get()), 1400)
                        .small().anchor(center, i == 0).turnAfter(330);
            }
            Net.fx(p, Fx.SUBTITLE, 90, 0, "Они стоят кругом у флагштока. Лицом друг к другу.");
        }));
        Scheduler.schedule(330, () -> near.accept(p -> HorrorUtil.playTo(p, "voice.camp_children", center.add(0, 1, 0), 2.5f, 1f)));
        Scheduler.schedule(470, () -> near.accept(p -> Net.fx(p, Fx.SUBTITLE, 70, 0, "Тридцать один. Тридцать два. Тридцать три.")));
        Scheduler.schedule(600, () -> {
            near.accept(p -> {
                Achievements.award(p, "lineup");
                Sanity.add(p, -10);
            });
        });
    }

    /** Three optional places scattered around the relay: lore, supplies and a little scene each. */
    private static void buildCamps(MinecraftServer server, StoryData d) {
        BlockPos radio = d.get("station_center");
        if (radio == null) return;
        campsPending = true;
        ServerLevel level = server.overworld();
        double base = level.random.nextDouble() * Math.PI * 2;
        for (int k = 0; k < 3; k++) {
            final int kind = k;
            final double ang = base + k * (Math.PI * 2 / 3);
            if (d.get("camp" + kind) != null) continue;
            Scheduler.schedule(60 + k * 30, () -> {
                if (kind == 2) campsPending = false;
                if (d.get("camp" + kind) != null) return;
                List<BlockPos> avoid = new ArrayList<>(d.pos.values());
                BlockPos site = Structures.findSite(level, radio, 90 + level.random.nextInt(60), 6, ang, avoid);
                Structures.buildCamp(level, site, d, kind);
            });
        }
    }

    /** Finishes a chapter change that was lost (server stopped during a scripted scene). */
    private static void recover(MinecraftServer server, StoryData d) {
        BlockPos near = d.get("origin") != null ? d.get("origin") : server.overworld().getSharedSpawnPos();
        ServerLevel level = server.overworld();
        if (d.chapter == CH_RELAY && d.flag("console_used")) {
            EchoHorror.LOG.info("Recovering lost transition -> village");
            ensureBuilt(server, CH_VILLAGE, near);
            setChapter(server, CH_VILLAGE);
        } else if (d.chapter == CH_VILLAGE && d.flag("bell_rung")) {
            EchoHorror.LOG.info("Recovering lost transition -> depths");
            ensureBuilt(server, CH_DEPTHS, near);
            setChapter(server, CH_DEPTHS);
        } else if (d.chapter == CH_DEPTHS && d.flag("gate_open")) {
            EchoHorror.LOG.info("Recovering lost transition -> object");
            for (BlockPos g : d.list("gate")) level.setBlock(g, Blocks.AIR.defaultBlockState(), 3);
            setChapter(server, CH_OBJECT);
        } else if (d.chapter == CH_OBJECT && (d.flag("power_on") || d.switchesOn.size() >= 3)) {
            EchoHorror.LOG.info("Recovering lost transition -> belfry");
            d.setFlag("power_on");
            for (BlockPos lamp : d.list("lamps")) level.setBlock(lamp, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            for (BlockPos g : d.list("belfry_door")) level.setBlock(g, Blocks.AIR.defaultBlockState(), 3);
            setChapter(server, CH_BELFRY);
        }
    }

    /** Places have their own voices: static at the relay, generators in the Object, clicking in the Depths. */
    private static void locationAmbience(ServerPlayer p, StoryData d) {
        net.minecraft.util.RandomSource r = p.getRandom();
        Vec3 pos = p.position();
        BlockPos console = d.get("console");
        if (console != null && pos.distanceToSqr(Vec3.atCenterOf(console)) < 18 * 18 && r.nextFloat() < 0.15f) {
            HorrorUtil.playTo(p, "scare.static", Vec3.atCenterOf(console), 0.35f, 0.8f + r.nextFloat() * 0.3f);
        }
        if (d.in("depths", pos) && r.nextFloat() < 0.07f) {
            HorrorUtil.playTo(p, "entity.crawler.click", pos.add(r.nextGaussian() * 18, -2, r.nextGaussian() * 18), 0.9f, 0.8f + r.nextFloat() * 0.3f);
        }
        if (d.in("bunker", pos) && d.flag("power_on") && r.nextFloat() < 0.12f) {
            HorrorUtil.playTo(p, "story.power", pos.add(r.nextGaussian() * 8, 2, r.nextGaussian() * 8), 0.25f, 0.55f);
        }
        if (d.in("village", pos) && HorrorUtil.isNight(p.serverLevel()) && r.nextFloat() < 0.03f) {
            BlockPos bell = d.get("bell");
            if (bell != null) {
                HorrorUtil.playTo(p, "story.bell_far", Vec3.atCenterOf(bell), 0.6f, 0.9f);
                // the bell-ringer is still up there
                if (d.chapter <= CH_VILLAGE && pos.distanceToSqr(Vec3.atCenterOf(bell)) > 20 * 20 && r.nextBoolean()) {
                    PhantomEntity.spawn(p, PhantomEntity.KIND_WATCHER, PhantomEntity.MODE_STARE,
                            Vec3.atBottomCenterOf(bell.below(2)).add(0.6, 0, 0), 240).vanishDistance(12).watchLimit(50);
                }
            }
        }
    }

    /** At night the camp is never quite empty. */
    private static void pioneerAmbience(ServerPlayer p, StoryData d) {
        BlockPos camp = d.get("pioneer");
        if (camp == null || !d.in("pioneer", p.position()) || !HorrorUtil.isNight(p.serverLevel()) || MusicBoxAura.protects(p)) return;
        net.minecraft.util.RandomSource r = p.getRandom();
        float roll = r.nextFloat();
        if (roll < 0.025f) {
            HorrorUtil.playTo(p, "voice.laugh", Vec3.atCenterOf(camp).add(r.nextGaussian() * 12, 1, r.nextGaussian() * 12), 0.7f, 1.4f);
        } else if (roll < 0.035f) {
            // a child in a cabin doorway
            BlockPos door = camp.offset(r.nextBoolean() ? -10 : 10, 0, 0);
            if (p.position().distanceToSqr(Vec3.atBottomCenterOf(door)) > 10 * 10)
                PhantomEntity.spawn(p, PhantomEntity.KIND_SHADE, PhantomEntity.MODE_STARE, Vec3.atBottomCenterOf(door), 400).small()
                        .vanishDistance(8).watchLimit(40);
        } else if (roll < 0.04f) {
            HorrorUtil.playTo(p, "voice.camp_children", Vec3.atCenterOf(camp).add(0, 1, 0), 0.5f, 0.9f);
        } else if (roll < 0.045f && d.get("pioneer_speaker") != null) {
            HorrorUtil.playTo(p, "story.camp_horn", Vec3.atCenterOf(d.get("pioneer_speaker")), 1.2f, 0.85f);
        }
    }

    /** A survivor in the burned house. He is helpful. For a while. */
    private static void spawnKuzmich(MinecraftServer server, StoryData d) {
        BlockPos v = d.get("village");
        if (v == null || !d.setFlag("kuzmich")) return;
        ServerLevel level = server.overworld();
        BlockPos at = v.offset(17, 0, 15); // inside the burned house
        MimicEntity k = new MimicEntity(ModEntities.MIMIC.get(), level);
        k.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
        k.asSurvivor("Пётр Кузьмич");
        level.addFreshEntity(k);
    }

    /** "It's me, Lisitsyn! In the church, quick!" It is not Lisitsyn. */
    private static void lisitsynLure(MinecraftServer server, StoryData d) {
        BlockPos bell = d.get("bell");
        if (bell == null) return;
        BlockPos door = new BlockPos(bell.getX(), bell.getY() - 13, bell.getZ() - 3); // in front of the church door
        Scheduler.schedule(160, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!d.in("village", p.position())) continue;
                p.sendSystemMessage(Component.translatable("chat.type.text", "Лисицын", "Сюда! Я в церкви! Быстрее, пока оно не вернулось!"));
                HorrorUtil.playTo(p, "voice.call", Vec3.atCenterOf(door), 2f, 1f);
                PhantomEntity.spawn(p, PhantomEntity.KIND_FAKE_PLAYER, PhantomEntity.MODE_STARE, Vec3.atBottomCenterOf(door), 1200)
                        .skin(java.util.UUID.nameUUIDFromBytes("Lisitsyn".getBytes()), "Лисицын").vanishDistance(6).watchLimit(400);
            }
        });
        Scheduler.schedule(900, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!d.in("village", p.position())) continue;
                p.sendSystemMessage(Component.translatable("chat.type.text", "Лисицын", "почему ты не идёшь ко мне"));
                Scheduler.schedule(60, () -> {
                    if (!p.hasDisconnected()) p.sendSystemMessage(Component.translatable("chat.type.text", "Лисицын",
                            Component.literal("почему ты не идёшь ко мне почему ты не идёшь ко мне почему").withStyle(ChatFormatting.DARK_RED)));
                });
            }
        });
    }

    public static boolean echoNight(StoryData d, Level level) {
        if (d.flag("ending_echo")) return HorrorUtil.isNight((ServerLevel) level);
        return d.chapter >= CH_VILLAGE && d.chapter < CH_SILENCE && HorrorUtil.isNight((ServerLevel) level)
                && d.echoDay >= 0 && level.getDayTime() / 24000L == d.echoDay;
    }

    private static boolean hasLocator(ServerPlayer p) {
        return p.getInventory().contains(new ItemStack(ModItems.LOCATOR.get()));
    }

    // =========================================================================================== locator
    public static BlockPos target(ServerPlayer p, StoryData d) {
        switch (d.chapter) {
            case CH_SIGNAL, CH_RELAY:
                return d.get("console");
            case CH_VILLAGE:
                if (!d.flag("clapper_installed")) {
                    if (p.getInventory().contains(new ItemStack(ModItems.BELL_CLAPPER.get()))) return d.get("bell");
                    return d.flag("cellar_scare") ? d.get("cellar") : d.get("village");
                }
                return d.get("bell");
            case CH_DEPTHS: {
                if (p.getInventory().contains(new ItemStack(ModItems.ECHO_SHARD.get()))) return d.get("lock");
                BlockPos best = null;
                double bd = Double.MAX_VALUE;
                for (BlockPos c : d.list("shard_chests")) {
                    ServerLevel ow = p.server.overworld();
                    if (!ow.isLoaded(c)) {
                        double dd = c.distSqr(p.blockPosition());
                        if (dd < bd) {
                            bd = dd;
                            best = c;
                        }
                        continue;
                    }
                    BlockEntity be = ow.getBlockEntity(c);
                    if (be instanceof Container cont && cont.hasAnyOf(java.util.Set.of(ModItems.ECHO_SHARD.get()))) {
                        double dd = c.distSqr(p.blockPosition());
                        if (dd < bd) {
                            bd = dd;
                            best = c;
                        }
                    }
                }
                return best != null ? best : d.get("lock");
            }
            case CH_OBJECT: {
                BlockPos best = null;
                double bd = Double.MAX_VALUE;
                for (BlockPos s : d.list("switches")) {
                    if (d.switchesOn.contains(s.asLong())) continue;
                    double dd = s.distSqr(p.blockPosition());
                    if (dd < bd) {
                        bd = dd;
                        best = s;
                    }
                }
                return best;
            }
            case CH_BELFRY:
                return d.get("arena");
            default:
                return null;
        }
    }

    public static void locatorTick(ServerPlayer p) {
        StoryData d = data(p.server);
        float sanity = Sanity.get(p);
        if (sanity < 30 && p.getRandom().nextFloat() < 0.12f) {
            String[] glitch = {"▮▮▮▮▮ С З А Д И ▮▮▮▮▮", "О Н О   З Д Е С Ь", "▮▮▮▮▮ 0 м ▮▮▮▮▮", "не ищи", "ТЫ ЕГО ИСТОЧНИК"};
            p.displayClientMessage(Component.literal(glitch[p.getRandom().nextInt(glitch.length)]).withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        BlockPos t = target(p, d);
        if (t == null || p.level() != p.server.overworld()) {
            p.displayClientMessage(Component.literal("· · · тишина · · ·").withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }
        double dx = t.getX() + 0.5 - p.getX(), dz = t.getZ() + 0.5 - p.getZ(), dy = t.getY() - p.getY();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yawTo = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        float rel = Mth.wrapDegrees(yawTo - p.getYRot());
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String arrow = arrows[Math.floorMod(Math.round(rel / 45f), 8)];
        int bars = dist < 15 ? 5 : dist < 50 ? 4 : dist < 120 ? 3 : dist < 300 ? 2 : 1;
        String strength = "▮".repeat(bars) + "▯".repeat(5 - bars);
        String vert = Math.abs(dy) > 6 ? (dy < 0 ? "  ▼ ниже" : "  ▲ выше") : "";
        String text = dist < 8 && Math.abs(dy) < 6 ? "◉ ИСТОЧНИК РЯДОМ  " + strength : arrow + "  " + (int) dist + " м  " + strength + vert;
        BlockPos pc = d.get("pioneer");
        boolean pioneerPing = false;
        if (pc != null && !d.flag("pioneer_seen")) {
            double cdx = pc.getX() + 0.5 - p.getX(), cdz = pc.getZ() + 0.5 - p.getZ();
            double cd = Math.sqrt(cdx * cdx + cdz * cdz);
            if (cd < 220) {
                float crel = Mth.wrapDegrees((float) (Mth.atan2(cdz, cdx) * (180F / Math.PI)) - 90F - p.getYRot());
                text += "   · детский голос " + arrows[Math.floorMod(Math.round(crel / 45f), 8)] + " " + (int) cd + " м";
                pioneerPing = true;
            }
        }
        for (int k = 0; k < 3 && !pioneerPing; k++) {
            BlockPos c = d.get("camp" + k);
            if (c == null || d.flag("camp_seen" + k)) continue;
            double cdx = c.getX() + 0.5 - p.getX(), cdz = c.getZ() + 0.5 - p.getZ();
            double cd = Math.sqrt(cdx * cdx + cdz * cdz);
            if (cd > 130) continue;
            float crel = Mth.wrapDegrees((float) (Mth.atan2(cdz, cdx) * (180F / Math.PI)) - 90F - p.getYRot());
            text += "   · слабый сигнал " + arrows[Math.floorMod(Math.round(crel / 45f), 8)] + " " + (int) cd + " м";
            break;
        }
        p.displayClientMessage(Component.literal(text).withStyle(bars >= 4 ? ChatFormatting.GREEN : ChatFormatting.DARK_GREEN), true);
        if (p.tickCount % 40 == 0) HorrorUtil.playAt(p, "scare.static", 0.05f + 0.06f * bars, 0.9f + bars * 0.05f);
    }

    // =========================================================================================== notes
    public static void readNote(ServerPlayer p, String id) {
        Notes.Note n = Notes.get(id);
        if (n == null) return;
        Net.fx(p, Fx.OPEN_NOTE, id);
        StoryData d = data(p.server);
        if (n.story() && d.notes.add(id)) {
            d.setDirty();
            long have = d.notes.stream().filter(x -> !x.startsWith("ending")).count();
            long total = Notes.all().values().stream().filter(x -> x.story() && !x.id().startsWith("ending")).count();
            if (have >= 10) Achievements.awardAll(p.server, "notes10");
            if (have >= total) Achievements.awardAll(p.server, "notes_all");
            broadcast(p.server, Component.literal("[Журнал] ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(p.getGameProfile().getName() + " нашёл запись: «" + n.title() + "»").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            syncAll(p.server);
            Sanity.add(p, -1.5f);
        } else if (!n.story()) {
            Sanity.add(p, -4f);
        }
    }

    public static boolean playTape(ServerPlayer p, String id) {
        Notes.Note n = Notes.get(id);
        if (n == null || !id.startsWith("tape")) return false;
        float seconds = switch (id) {
            case "tape1" -> 23.3f;
            case "tape2" -> 23.7f;
            case "tape3" -> 26.6f;
            case "tape5" -> 21.3f;
            case "tape6" -> 33.7f;
            case "tape7" -> 32.1f;
            default -> 31.1f;
        };
        SoundSeqPacket seq = subtitled("tape." + id, n.text().replace("«", "").replace("»", ""), seconds, 0.6f);
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o.level() == p.level() && o.distanceTo(p) < 20) Net.send(o, seq);
        }
        StoryData d = data(p.server);
        if (d.notes.add(id)) {
            d.setDirty();
            syncAll(p.server);
        }
        return true;
    }

    /** One sound with subtitles distributed over its duration proportionally to sentence length. */
    public static SoundSeqPacket subtitled(String sound, String text, float seconds, float lead) {
        String[] parts = text.split("(?<=[.!?])\\s+");
        int total = 0;
        for (String s : parts) total += s.length();
        SoundSeqPacket.Builder b = new SoundSeqPacket.Builder();
        b.sound(sound, 1, "");
        int delay = Math.round(lead * 20);
        float speech = (seconds - lead - 0.8f) * 20f;
        for (String s : parts) {
            b.sub("«" + s.trim() + "»", Math.max(1, delay));
            delay = Math.round(speech * s.length() / (float) total);
        }
        b.sub("", Math.max(20, delay));
        return b.build();
    }

    // =========================================================================================== chapter 2: console
    public static void onConsoleUse(ServerPlayer p, BlockPos pos) {
        MinecraftServer server = p.server;
        StoryData d = data(server);
        if (d.chapter == CH_SIGNAL) setChapter(server, CH_RELAY);
        if (d.chapter < CH_RELAY) {
            p.displayClientMessage(Component.literal("Мёртвая аппаратура.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ServerLevel level = p.serverLevel();
        if (d.chapter > CH_RELAY || d.flag("console_used")) {
            HorrorUtil.playTo(p, "scare.static", Vec3.atCenterOf(pos), 0.8f, 0.8f);
            p.displayClientMessage(Component.literal("Из динамика — только дыхание.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
            return;
        }
        if (!HorrorUtil.isNight(level)) {
            HorrorUtil.playTo(p, "scare.static", Vec3.atCenterOf(pos), 0.6f, 1f);
            p.displayClientMessage(Component.literal("Передатчик молчит. Эфир открывается только ночью.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return;
        }
        d.setFlag("console_used");
        beginTransition();
        syncAll(server);
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer o : server.getPlayerList().getPlayers()) if (o.level() == level && o.distanceTo(p) < 48) near.add(o);
        SoundSeqPacket.Builder b = new SoundSeqPacket.Builder()
                .sound("radio.tune", 1, "[ щелчок тумблера ]")
                .sound("radio.intro", 30, "[ позывной ]")
                .sound("radio.b_lis", 170, "«Это Лисицын. Ретранслятор Р-7.»")
                .sub("«Если вы слышите это, значит, я уже не я.»", 70)
                .sub("«Оно выучило мой голос.»", 90)
                .sub("«Найдите колокол. Тихий Лог.»", 70)
                .sub("«Только колокол заставляет его замолчать.»", 80)
                .sub("", 110);
        for (ServerPlayer o : near) Net.send(o, b.build());

        // build the village now; it will be ready when the broadcast ends
        Scheduler.schedule(40, () -> {
            BlockPos radio = d.get("station_center") != null ? d.get("station_center") : pos;
            BlockPos origin = d.get("origin") != null ? d.get("origin") : radio;
            double ang = radio.equals(origin) ? Double.NaN : Math.atan2(radio.getZ() - origin.getZ(), radio.getX() - origin.getX());
            BlockPos site = Structures.findSite(server.overworld(), radio, Config.LOCATION_DISTANCE.get(), 34, ang);
            Structures.buildVillage(server.overworld(), site, d);
        });
        // the set piece
        Scheduler.schedule(560, () -> {
            for (ServerPlayer o : near) {
                if (o.hasDisconnected()) continue;
                Net.fx(o, Fx.BLACKOUT, 30);
                HorrorUtil.playAt(o, "scare.stinger", 1f, 0.8f);
                BlockPos door = d.get("radio_door");
                if (door != null && o.distanceToSqr(Vec3.atCenterOf(door)) < 40 * 40) {
                    HorrorUtil.playTo(o, "scare.knock_frantic", Vec3.atCenterOf(door), 1.5f, 1f);
                    Scheduler.schedule(40, () -> {
                        if (!o.hasDisconnected()) PhantomEntity.spawn(o, PhantomEntity.KIND_WATCHER, PhantomEntity.MODE_STARE,
                                Vec3.atBottomCenterOf(door.north(2)), 400).vanishDistance(2.5).withJumpscare();
                    });
                }
                Sanity.add(o, -10f);
            }
            Scheduler.schedule(80, () -> {
                for (ServerPlayer o : near) if (!o.hasDisconnected()) HorrorUtil.playAt(o, "voice.laugh", 0.6f, 0.9f);
            });
            Scheduler.schedule(160, () -> {
                if (data(server).get("bell") == null) ensureBuilt(server, CH_VILLAGE, server.overworld().getSharedSpawnPos());
                setChapter(server, CH_VILLAGE);
                endTransition();
            });
        });
    }

    // =========================================================================================== chapter 3: village
    private static void cellarScare(ServerPlayer p, StoryData d) {
        ServerLevel level = p.serverLevel();
        BlockPos hatch = d.get("cellar_hatch");
        if (hatch != null && level.getBlockState(hatch).getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
            level.setBlock(hatch, level.getBlockState(hatch).setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, false), 3);
            level.playSound(null, hatch, net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.5f);
        }
        Net.fx(p, Fx.FLICKER, 60);
        Scheduler.schedule(30, () -> HorrorUtil.playTo(p, "whisper", HorrorUtil.behind(p, 1.5), 1f, 0.8f));
        Scheduler.schedule(70, () -> {
            HorrorUtil.playTo(p, "scare.knock", Vec3.atCenterOf(hatch != null ? hatch : p.blockPosition()), 1.5f, 0.8f);
            Sanity.add(p, -6f);
        });
    }

    /** Called for any bell interaction. Returns true if the vanilla interaction must be cancelled. */
    public static boolean onBellUse(ServerPlayer p, BlockPos pos) {
        StoryData d = data(p.server);
        BlockPos bell = d.get("bell");
        if (bell == null || !bell.equals(pos) || d.chapter != CH_VILLAGE) return false;
        if (!d.flag("clapper_installed")) {
            ItemStack held = p.getMainHandItem();
            if (held.is(ModItems.BELL_CLAPPER.get())) {
                if (!p.getAbilities().instabuild) held.shrink(1);
                d.setFlag("clapper_installed");
                Achievements.award(p, "clapper");
                p.serverLevel().playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1f, 0.6f);
                broadcast(p.server, Component.literal(p.getGameProfile().getName() + " повесил язык колокола на место.").withStyle(ChatFormatting.GOLD));
                syncAll(p.server);
            } else {
                p.displayClientMessage(Component.literal("Колокол нем. У него нет языка.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            }
            return true;
        }
        if (!HorrorUtil.isMidnight(p.serverLevel())) {
            p.displayClientMessage(Component.literal("Звон глохнет, будто в вату. Ещё не время — нужна полночь.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return false;
        }
        if (d.setFlag("bell_rung")) bellEvent(p.server, pos);
        return false;
    }

    private static void bellEvent(MinecraftServer server, BlockPos bellPos) {
        StoryData d = data(server);
        ServerLevel level = server.overworld();
        beginTransition();
        level.playSound(null, bellPos, ModSounds.get("story.bell"), SoundSource.BLOCKS, 8f, 0.8f);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Net.fx(p, Fx.SHAKE, 60, 1.5f, "");
            if (p.distanceToSqr(Vec3.atCenterOf(bellPos)) > 128 * 128) HorrorUtil.playAt(p, "story.bell_far", 1f, 0.8f);
        }
        Scheduler.schedule(120, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Net.send(p, new SoundSeqPacket.Builder().sound("radio.b_heard", 1, "«Мы услышали вас.»").sub("", 120).build());
                Sanity.add(p, -8f);
            }
        });
        Scheduler.schedule(30, () -> Structures.buildDepths(level, d));
        Scheduler.schedule(70, () -> Structures.buildBunker(level, d));
        Scheduler.schedule(110, () -> Structures.buildBelfry(level, d));
        Scheduler.schedule(200, () -> {
            BlockPos well = d.get("well");
            if (well != null) {
                level.setBlock(well, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, well.getX() + 0.5, well.getY() + 0.5, well.getZ() + 0.5, 60, 0.4, 0.8, 0.4, 0.05);
                level.playSound(null, well, ModSounds.get("scare.scream_far"), SoundSource.HOSTILE, 4f, 0.7f);
            }
            broadcast(server, Component.literal("Из колодца тянет тёплым воздухом. Крышка сорвана изнутри.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            setChapter(server, CH_DEPTHS);
            endTransition();
        });
    }

    // =========================================================================================== chapter 4: depths
    public static void onShardInserted(ServerPlayer p, BlockPos pos, int n) {
        MinecraftServer server = p.server;
        StoryData d = data(server);
        d.setFlag("lock" + n);
        broadcast(server, Component.literal("Печать: " + n + "/3").withStyle(ChatFormatting.GOLD));
        syncAll(server);
        if (n < 3 || !d.setFlag("gate_open")) return;
        beginTransition();
        ServerLevel level = p.serverLevel();
        List<BlockPos> gate = new ArrayList<>(d.list("gate"));
        level.playSound(null, pos, ModSounds.get("story.door_open"), SoundSource.BLOCKS, 4f, 0.8f);
        for (int i = 0; i < gate.size(); i++) {
            BlockPos g = gate.get(i);
            Scheduler.schedule(10 + i * 8, () -> {
                level.setBlock(g, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.CLOUD, g.getX() + 0.5, g.getY() + 0.5, g.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
            });
        }
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            Net.send(o, new SoundSeqPacket.Builder().sound("radio.b_deeper", 80, "«Глубже. Спускайтесь глубже. Мы ждём.»").sub("", 120).build());
        }
        Scheduler.schedule(120, () -> {
            setChapter(server, CH_OBJECT);
            endTransition();
        });
    }

    private static void ambush(ServerPlayer p, int count) {
        if (!Config.HOSTILE_SPAWNS.get()) return;
        ServerLevel level = p.serverLevel();
        HorrorUtil.playTo(p, "entity.crawler.click", HorrorUtil.behind(p, 8), 1.5f, 0.8f);
        Scheduler.schedule(40, () -> {
            for (int i = 0; i < count; i++) {
                Optional<Vec3> spot = HorrorUtil.spotAround(p, 9, 18, 180, 120, false);
                spot.ifPresent(s -> {
                    CrawlerEntity c = new CrawlerEntity(ModEntities.CRAWLER.get(), level);
                    c.moveTo(s.x, s.y, s.z, level.random.nextFloat() * 360, 0);
                    c.setTarget(p);
                    level.addFreshEntity(c);
                });
            }
        });
    }

    // =========================================================================================== chapter 5: object
    private static void archiveMimic(ServerPlayer p, StoryData d) {
        List<ServerPlayer> others = HorrorUtil.others(p);
        ServerPlayer face = others.isEmpty() ? p : others.get(p.getRandom().nextInt(others.size()));
        StoryData.Box box = d.regions.get("archive");
        if (box == null) return;
        Vec3 spot = new Vec3(box.x1() + 3.5, box.y1() + 1, box.z2() - 2.5);
        MimicEntity m = MimicEntity.spawnAs(p.serverLevel(), spot, face);
        m.daylightImmune();
        m.setTarget(p);
    }

    public static void onSwitch(ServerPlayer p, BlockPos pos) {
        MinecraftServer server = p.server;
        StoryData d = data(server);
        if (!d.switchesOn.add(pos.asLong())) return;
        d.setDirty();
        ServerLevel level = p.serverLevel();
        level.playSound(null, pos, ModSounds.get("story.power"), SoundSource.BLOCKS, 3f, 0.7f + d.switchesOn.size() * 0.15f);
        broadcast(server, Component.literal("Питание: " + d.switchesOn.size() + "/3").withStyle(ChatFormatting.GOLD));
        syncAll(server);
        // every switch wakes something up
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            if (d.in("bunker", o.position())) Net.fx(o, Fx.FLICKER, 40);
        }
        if (d.switchesOn.size() < 3 || d.chapter != CH_OBJECT || !d.setFlag("power_on")) return;
        beginTransition();
        for (BlockPos lamp : d.list("lamps")) level.setBlock(lamp, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        List<BlockPos> door = new ArrayList<>(d.list("belfry_door"));
        for (int i = 0; i < door.size(); i++) {
            BlockPos g = door.get(i);
            Scheduler.schedule(60 + i * 6, () -> level.setBlock(g, Blocks.AIR.defaultBlockState(), 3));
        }
        if (!door.isEmpty()) level.playSound(null, door.get(0), ModSounds.get("story.door_open"), SoundSource.BLOCKS, 4f, 0.7f);
        SoundSeqPacket tape = subtitled("tape.tape4", Notes.get("tape4").text().replace("«", "").replace("»", ""), 31.1f, 0.6f);
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            if (d.in("bunker", o.position()) || o.distanceTo(p) < 64) Net.send(o, tape);
        }
        d.notes.add("tape4");
        Scheduler.schedule(660, () -> {
            setChapter(server, CH_BELFRY);
            endTransition();
        });
        Scheduler.schedule(760, () -> blackout(server, d));
    }

    private static boolean blackoutActive;

    /** Admin: replays a scripted scene. */
    public static boolean scene(MinecraftServer server, String name) {
        StoryData d = data(server);
        switch (name) {
            case "lineup" -> {
                if (d.get("pioneer") == null) return false;
                lineup(server, d);
                return true;
            }
            case "blackout" -> {
                if (blackoutActive || d.get("depths") == null) return false;
                d.flags.remove("blackout");
                d.flags.remove("blackout_end");
                blackout(server, d);
                return d.flag("blackout");
            }
            default -> {
                return false;
            }
        }
    }

    private static void setLamps(ServerLevel level, List<BlockPos> lamps, boolean on) {
        for (BlockPos l : lamps) level.setBlock(l, on ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState(), 3);
    }

    /** Обесточивание: the lights die, the siren wails, and the blind ones come out. Sneak to the stairs. */
    private static void blackout(MinecraftServer server, StoryData d) {
        ServerLevel level = server.overworld();
        BlockPos origin = d.get("depths");
        List<ServerPlayer> inside = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && d.in("bunker", p.position())) inside.add(p);
        if (origin == null || inside.isEmpty() || !d.setFlag("blackout")) {
            EchoHorror.LOG.info("Blackout skipped: origin={}, inside={}, players={}", origin, inside.size(), level.players().size());
            d.setFlag("blackout_end");
            return;
        }
        blackoutActive = true;
        List<BlockPos> lamps = new ArrayList<>(d.list("lamps"));
        for (int i = 0; i < 7; i++) {
            final boolean on = i % 2 == 1 && i < 6;
            Scheduler.schedule(i * 4, () -> setLamps(level, lamps, on));
        }
        Vec3 corridor = Vec3.atCenterOf(origin.offset(0, 2, 62));
        for (ServerPlayer p : inside) {
            Net.fx(p, Fx.FLICKER, 30);
            Scheduler.schedule(26, () -> {
                Net.fx(p, Fx.BLACKOUT, 20);
                Net.fx(p, Fx.SUBTITLE, 120, 0, "Свет погас. Не шуми. К лестнице — пригнувшись.");
            });
        }
        for (int k = 0; k < 4; k++) {
            Scheduler.schedule(30 + k * 340, () -> {
                if (!blackoutActive) return;
                for (ServerPlayer p : level.players())
                    if (d.in("bunker", p.position()) || d.in("stairs", p.position())) HorrorUtil.playTo(p, "story.siren", corridor, 4f, 0.9f);
            });
        }
        Scheduler.schedule(110, () -> {
            for (ServerPlayer p : level.players())
                if (d.in("bunker", p.position())) HorrorUtil.playTo(p, "voice.bunker_alarm", corridor, 4f, 1f);
        });
        // they come from the entrance, behind you
        if (Config.HOSTILE_SPAWNS.get()) {
            int n = 2 + inside.size();
            int[][] spots = {{0, 40}, {7, 44}, {-7, 44}, {0, 50}, {6, 62}, {-10, 62}};
            Scheduler.schedule(140, () -> {
                for (int i = 0; i < n; i++) {
                    int[] sp = spots[i % spots.length];
                    CrawlerEntity c = new CrawlerEntity(ModEntities.CRAWLER.get(), level);
                    BlockPos at = origin.offset(sp[0], 0, sp[1]);
                    c.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
                    c.setPersistenceRequired();
                    level.addFreshEntity(c);
                }
                for (ServerPlayer p : level.players())
                    if (d.in("bunker", p.position())) HorrorUtil.playTo(p, "entity.crawler.click", corridor.add(0, -1, -14), 2f, 0.8f);
            });
        }
        // lights come back once nobody is left upstairs, or after a minute and a half
        for (int t = 200; t <= 1800; t += 40) {
            final boolean last = t >= 1800;
            Scheduler.schedule(t, () -> {
                if (!blackoutActive) return;
                boolean anyone = false;
                for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.isAlive() && d.in("bunker", p.position())) anyone = true;
                if (anyone && !last) return;
                endBlackout(level, d);
            });
        }
    }

    private static void endBlackout(ServerLevel level, StoryData d) {
        blackoutActive = false;
        setLamps(level, d.list("lamps"), true);
        d.setFlag("blackout_end");
    }

    // =========================================================================================== chapter 6: belfry
    private static void belfryTick(MinecraftServer server, ServerPlayer p, StoryData d) {
        ServerLevel level = p.serverLevel();
        BlockPos arena = d.get("arena");
        if (arena == null) return;
        if (!d.flag("boss_spawned")) {
            d.setFlag("boss_spawned");
            syncAll(server);
            for (ServerPlayer o : server.getPlayerList().getPlayers()) {
                if (!d.in("arena", o.position()) && o.distanceToSqr(Vec3.atCenterOf(arena)) > 80 * 80) continue;
                Net.fx(o, Fx.BLACKOUT, 40);
                Net.fx(o, Fx.CHAPTER, "ОТГОЛОСОК|Звоните в колокола");
                Net.fx(o, Fx.MUSIC, "music.finale");
            }
            Scheduler.schedule(40, () -> {
                EchoBossEntity.spawn(level, arena);
                level.playSound(null, arena, ModSounds.get("entity.boss.roar"), SoundSource.HOSTILE, 6f, 0.7f);
            });
            return;
        }
        // safety: boss vanished (e.g. removed by a command) while the fight is unfinished
        if (level.getGameTime() % 200 < 20 && d.chapter == CH_BELFRY && !bossRespawnPending) {
            StoryData.Box box = d.regions.get("arena");
            if (box != null && !d.flag("boss_dead") && level.getEntitiesOfClass(EchoBossEntity.class, box.aabb().inflate(16)).isEmpty()) {
                bossRespawnPending = true;
                Scheduler.schedule(200, () -> {
                    if (d.chapter == CH_BELFRY && !d.flag("boss_dead") && level.getEntitiesOfClass(EchoBossEntity.class, box.aabb().inflate(16)).isEmpty()) {
                        EchoBossEntity.spawn(level, arena);
                    }
                    bossRespawnPending = false;
                });
            }
        }
    }

    public static void onBossDefeated(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        StoryData d = data(server);
        if (d.chapter != CH_BELFRY || !d.setFlag("boss_dead")) return;
        BlockPos center = d.get("arena") != null ? d.get("arena") : pos;
        // the rope of the Great Bell comes down; the heart of the Echo is still beating on the floor
        for (int y = 2; y <= 8; y++) level.setBlock(center.above(y), Blocks.CHAIN.defaultBlockState(), 3);
        level.setBlock(center.above(1), com.echohorror.registry.ModBlocks.GREAT_BELL_ROPE.get().defaultBlockState(), 3);
        level.setBlock(center, Blocks.AIR.defaultBlockState(), 3);
        net.minecraft.world.entity.item.ItemEntity heart = new net.minecraft.world.entity.item.ItemEntity(level,
                center.getX() + 2.5, center.getY() + 0.5, center.getZ() + 0.5, new ItemStack(ModItems.ECHO_HEART.get()));
        heart.setUnlimitedLifetime();
        heart.setGlowingTag(true);
        heart.setDeltaMovement(0, 0.2, 0);
        level.addFreshEntity(heart);
        d.put("rope", center.above(1));
        Achievements.awardAll(server, "boss");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Net.fx(p, Fx.MUSIC, "");
            Sanity.add(p, 40f);
            Net.fx(p, Fx.CHAPTER, "ВЫБОР|Позвонить — или послушать");
            p.sendSystemMessage(Component.literal("Отголосок рассыпался. Над головой — Великий колокол, с него свисает верёвка.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            p.sendSystemMessage(Component.literal("На полу что-то бьётся. Сердце Эха. Оно шепчет: «послушай меня. только один раз».")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            p.sendSystemMessage(Component.literal("Дёрнуть верёвку — заставить его замолчать навсегда. Поднести сердце к уху — ...")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        syncAll(server);
    }

    /** The final choice. {@code listen} = the bad ending. Returns true if the choice was accepted. */
    public static boolean chooseEnding(ServerPlayer chooser, boolean listen) {
        MinecraftServer server = chooser.server;
        StoryData d = data(server);
        if (d.chapter != CH_BELFRY || !d.flag("boss_dead")) {
            chooser.displayClientMessage(Component.literal(listen ? "Тишина в ответ." : "Верёвка не поддаётся. Ещё не время.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return false;
        }
        if (!d.setFlag("ending_chosen")) return false;
        String name = chooser.getGameProfile().getName();
        if (listen) endingEcho(server, d, name);
        else endingSilence(server, d, name);
        return true;
    }

    private static void endingSilence(MinecraftServer server, StoryData d, String name) {
        d.setFlag("ending_silence");
        Achievements.awardAll(server, "ending_silence");
        d.notes.add("ending");
        BlockPos rope = d.get("rope");
        ServerLevel level = server.overworld();
        if (rope != null) level.playSound(null, rope, ModSounds.get("story.bell"), SoundSource.BLOCKS, 10f, 0.5f);
        // the heart dies with the sound
        for (net.minecraft.world.entity.item.ItemEntity ie : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(rope != null ? rope : BlockPos.ZERO).inflate(40), e -> e.getItem().is(ModItems.ECHO_HEART.get()))) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, ie.getX(), ie.getY(), ie.getZ(), 30, 0.3, 0.3, 0.3, 0.05);
            ie.discard();
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.getInventory().clearOrCountMatchingItems(st -> st.is(ModItems.ECHO_HEART.get()), -1, p.inventoryMenu.getCraftSlots());
            p.sendSystemMessage(Component.literal(name + " дёрнул верёвку Великого колокола.").withStyle(ChatFormatting.GOLD));
            Net.fx(p, Fx.WHITE_FLASH, 40);
            Net.fx(p, Fx.SHAKE, 60, 2f, "");
        }
        setChapter(server, CH_SILENCE);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Net.fx(p, Fx.CHAPTER, "ЭПИЛОГ|Тишина");
            Sanity.set(p, 100f);
            give(p, new ItemStack(ModItems.SILENCE.get()));
            Net.send(p, new SoundSeqPacket.Builder()
                    .sound("radio.tune", 120, "[ пеленгатор оживает в последний раз ]")
                    .sound("radio.b_attention", 40, "«Внимание. Внимание. Говорит ретранслятор Р-7. Считаю.»")
                    .sound("radio.num0", 175, "«...ноль.»")
                    .sub("", 60)
                    .sub("[ тишина ]", 100)
                    .sound("radio.num1", 160, "«...один.»")
                    .sub("", 60)
                    .build());
        }
        Scheduler.schedule(740, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Net.fx(p, Fx.CREDITS, 0);
                Net.fx(p, Fx.MUSIC, "music.ending");
            }
        });
        // the echo never truly leaves
        Scheduler.schedule(20 * 60 * 12, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.translatable("chat.type.text", p.getGameProfile().getName(), "я всё ещё здесь"));
                Scheduler.schedule(100, () -> {
                    if (!p.hasDisconnected())
                        p.sendSystemMessage(Component.translatable("multiplayer.player.joined", "Эхо").withStyle(ChatFormatting.YELLOW));
                });
            }
        });
    }

    private static void endingEcho(MinecraftServer server, StoryData d, String name) {
        d.setFlag("ending_echo");
        Achievements.awardAll(server, "ending_echo");
        d.notes.add("ending_echo");
        BlockPos rope = d.get("rope");
        ServerLevel level = server.overworld();
        if (rope != null) {
            level.setBlock(rope, Blocks.AIR.defaultBlockState(), 3); // the rope snaps
            level.sendParticles(ParticleTypes.SCULK_SOUL, rope.getX() + 0.5, rope.getY() + 1, rope.getZ() + 0.5, 80, 1, 2, 1, 0.1);
        }
        int count = server.getPlayerList().getPlayerCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Net.fx(p, Fx.GLITCH, 40);
            Net.fx(p, Fx.BLACKOUT, 30);
            Net.fx(p, Fx.SHAKE, 80, 2.5f, "");
            p.sendSystemMessage(Component.literal(name + " поднёс сердце Эха к уху.").withStyle(ChatFormatting.DARK_RED));
            p.sendSystemMessage(Component.translatable("chat.type.text", name, "я слышу вас всех").withStyle(ChatFormatting.DARK_RED));
        }
        setChapter(server, CH_SILENCE);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Net.fx(p, Fx.CHAPTER, "ЭПИЛОГ|Эхо");
            Sanity.set(p, 30f);
            SoundSeqPacket.Builder b = new SoundSeqPacket.Builder()
                    .sound("radio.b_heard", 40, "«Мы услышали вас.»")
                    .sound("radio.b_attention", 140, "«Внимание. Внимание. Говорит ретранслятор Р-7. Считаю.»");
            String[] words = {"ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять", "десять", "одиннадцать", "двенадцать"};
            int delay = 175;
            for (int i = 1; i <= Math.min(12, count + 4); i++) {
                b.sound("radio.num" + i, delay, "«" + words[i] + "...»");
                delay = 26;
            }
            b.sound("radio.b_more", 50, "«Вас стало больше.»").sub("«Нас стало больше.»", 70).sub("", 60);
            Net.send(p, b.build());
        }
        Scheduler.schedule(820, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Net.fx(p, Fx.CREDITS, 1);
                Net.fx(p, Fx.MUSIC, "music.ending");
            }
        });
    }

    /** The bad ending never ends: the world keeps behaving as if the Belfry were still open. */
    public static int effectiveChapter(StoryData d) {
        return d.chapter == CH_SILENCE && d.flag("ending_echo") ? CH_BELFRY : d.chapter;
    }

    // =========================================================================================== admin
    /** Makes sure the structures for the given chapter exist (used by /echo chapter). */
    public static void ensureBuilt(MinecraftServer server, int ch, BlockPos near) {
        StoryData d = data(server);
        ServerLevel level = server.overworld();
        if (ch >= CH_SIGNAL && d.get("console") == null) {
            if (d.get("origin") == null) d.put("origin", near);
            Structures.buildRadio(level, Structures.findSite(level, near, Config.LOCATION_DISTANCE.get(), 12), d);
            d.notes.add("prologue");
        }
        if (ch >= CH_VILLAGE && d.get("bell") == null) {
            BlockPos r = d.get("station_center") != null ? d.get("station_center") : near;
            BlockPos o = d.get("origin") != null ? d.get("origin") : r;
            double ang = r.equals(o) ? Double.NaN : Math.atan2(r.getZ() - o.getZ(), r.getX() - o.getX());
            Structures.buildVillage(level, Structures.findSite(level, r, Config.LOCATION_DISTANCE.get(), 34, ang), d);
        }
        if (ch >= CH_DEPTHS && d.get("lock") == null) Structures.buildDepths(level, d);
        if (ch >= CH_DEPTHS && d.get("bunker") == null) Structures.buildBunker(level, d);
        if (ch >= CH_DEPTHS && d.get("arena") == null) Structures.buildBelfry(level, d);
        if (ch >= CH_DEPTHS) {
            BlockPos well = d.get("well");
            if (well != null) level.setBlock(well, Blocks.AIR.defaultBlockState(), 3);
            d.setFlag("clapper_installed");
            d.setFlag("bell_rung");
        }
        if (ch >= CH_OBJECT && d.setFlag("gate_open")) {
            for (BlockPos g : d.list("gate")) level.setBlock(g, Blocks.AIR.defaultBlockState(), 3);
        }
        if (ch >= CH_BELFRY && d.setFlag("power_on")) {
            for (BlockPos lamp : d.list("lamps")) level.setBlock(lamp, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            for (BlockPos g : d.list("belfry_door")) level.setBlock(g, Blocks.AIR.defaultBlockState(), 3);
        }
        if (ch >= CH_VILLAGE) d.setFlag("console_used");
        d.setDirty();
    }

    public static void reset(MinecraftServer server) {
        StoryData d = data(server);
        Scheduler.clear();
        pendingTransitions = 0;
        bossRespawnPending = false;
        campsPending = false;
        pioneerPending = false;
        d.echoDay = -1;
        d.chapter = 0;
        d.pos.clear();
        d.lists.clear();
        d.regions.clear();
        d.notes.clear();
        d.flags.clear();
        d.kits.clear();
        d.switchesOn.clear();
        d.nights = 0;
        d.lastCountDay = -1;
        d.setDirty();
        firstPlayerTicks = 0;
        syncAll(server);
    }

    public static ItemStack noteStack(String id) {
        return NoteItem.create(id);
    }
}
