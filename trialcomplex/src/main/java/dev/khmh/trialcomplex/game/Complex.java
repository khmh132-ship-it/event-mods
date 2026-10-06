package dev.khmh.trialcomplex.game;

import dev.khmh.trialcomplex.TrialComplex;
import dev.khmh.trialcomplex.cmd.PuzzleCommand;
import dev.khmh.trialcomplex.net.Net;
import dev.khmh.trialcomplex.voice.Voice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

/** Сердце карты: этапы, чекпоинты, голос, защита. Один экземпляр на запущенный мир. */
public final class Complex {
    private static Complex instance;

    private final MinecraftServer server;
    private final ServerLevel level;
    private final ProgressData data;
    private final Voice voice;
    private final Roles roles;
    private final List<Room> rooms;
    private final List<Task> tasks = new ArrayList<>();
    private final Map<UUID, BlockPos> lastFeet = new HashMap<>();
    private final Set<UUID> bypass = new HashSet<>();
    private long tick;
    private boolean hudDirty = true;
    private long lastIdleTaunt, lastErrorLog = -1000;
    private boolean waitingAnnounced;
    private final Set<Integer> digitsAnnounced = new HashSet<>();

    private record Task(long due, Room owner, int epoch, Runnable run) {}

    private Complex(MinecraftServer server) {
        this.server = server;
        this.level = server.overworld();
        this.data = level.getDataStorage().computeIfAbsent(ProgressData::load, ProgressData::new, ProgressData.NAME);
        this.voice = new Voice(this);
        this.roles = new Roles(server);
        this.rooms = Layout.create();
        Layout.place(this, rooms);
    }

    public static Complex get() {
        return instance;
    }

    // ---------- доступ ----------

    public ServerLevel level() { return level; }
    public MinecraftServer server() { return server; }
    public ProgressData data() { return data; }
    public Voice voice() { return voice; }
    public Roles roles() { return roles; }
    public List<Room> rooms() { return rooms; }
    public long tick() { return tick; }
    public void hudDirty() { hudDirty = true; }

    public List<ServerPlayer> players() {
        return server.getPlayerList().getPlayers();
    }

    public Room current() {
        return data.stage < rooms.size() ? rooms.get(data.stage) : null;
    }

    public Room room(String id) {
        for (Room r : rooms) if (r.id.equals(id)) return r;
        return null;
    }

    public boolean bypass(ServerPlayer p) {
        return bypass.contains(p.getUUID());
    }

    public boolean toggleBypass(ServerPlayer p) {
        if (!bypass.remove(p.getUUID())) {
            bypass.add(p.getUUID());
            return true;
        }
        return false;
    }

    // ---------- старт ----------

    private void init() {
        GameRules gr = level.getGameRules();
        gr.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        gr.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        gr.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        gr.getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        gr.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, server);
        gr.getRule(GameRules.RULE_SHOWDEATHMESSAGES).set(false, server);
        gr.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
        gr.getRule(GameRules.RULE_DOFIRETICK).set(false, server);
        gr.getRule(GameRules.RULE_MOBGRIEFING).set(false, server);
        gr.getRule(GameRules.RULE_FALL_DAMAGE).set(false, server);
        gr.getRule(GameRules.RULE_SPAWN_RADIUS).set(0, server);
        gr.getRule(GameRules.RULE_DISABLE_RAIDS).set(true, server);
        gr.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, server);
        gr.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, server);
        level.setDayTime(18000);
        server.setDifficulty(Difficulty.PEACEFUL, true);

        if (Boolean.getBoolean("trialcomplex.autobuild")) return; // постройка идёт через DevTools

        // Текущая комната могла остаться в полурешённом виде — пересобираем её начисто.
        Room cur = current();
        if (cur != null && data.started) {
            cur.buildAll(level);
            for (int i = 0; i < data.stage; i++) rooms.get(i).exit.setInstant(level, true);
            cur.onReset();
        }
    }

    // ---------- тик ----------

    private void onTick() {
        tick++;
        runTasks();
        voice.tick(tick);

        List<ServerPlayer> ps = players();
        // ошибка в логике комнаты не должна ронять мир — логируем и продолжаем
        try {
            for (ServerPlayer p : ps) tickPlayer(p);
            Room cur = current();
            if (cur != null) tickRoom(cur, ps);
        } catch (Exception e) {
            if (tick - lastErrorLog > 200) {
                lastErrorLog = tick;
                TrialComplex.LOG.error("Ошибка в логике комплекса", e);
            }
        }

        if (tick % 10 == 0) checkDigitConnectors(ps);

        if (hudDirty || tick % 100 == 0) {
            hudDirty = false;
            for (ServerPlayer p : ps) sendHud(p);
        }
    }

    private void tickPlayer(ServerPlayer p) {
        boolean free = bypass(p);
        if (!free && tick % 10 == 0 && p.gameMode.getGameModeForPlayer() != GameType.ADVENTURE)
            p.setGameMode(GameType.ADVENTURE);
        if (tick % 40 == 0) {
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(5f);
        }
        if (free) return;

        Room cur = current();
        double voidY = cur != null ? cur.voidY() : 0;
        if (p.getY() < voidY) {
            data.inc(data.falls, roles.key(p));
            if (cur != null && cur.isActive() && cur.onFall(p)) return;
            toCheckpoint(p);
            voice.sayGroupFor("fall", p);
            return;
        }
        if (p.onGround()) {
            BlockPos feet = p.blockPosition();
            BlockPos prev = lastFeet.put(p.getUUID(), feet);
            if (!feet.equals(prev) && cur != null && cur.isActive()) cur.onStep(p, feet.below());
        }
    }

    private void tickRoom(Room cur, List<ServerPlayer> ps) {
        if (!cur.activated) {
            boolean anyInside = ps.stream().anyMatch(cur::inside);
            if (!anyInside) return;
            if (data.stage == 0 && !data.started) {
                if (ps.size() < 2) {
                    if (!waitingAnnounced) {
                        waitingAnnounced = true;
                        voice.say("intro.wait");
                    }
                    return;
                }
                startGame();
            }
            activate(cur);
            return;
        }
        if (cur.solved) return;

        if (cur.hasEntry() && cur.entry.isOpen(level) && cur.lockEntryWhenAllInside() && !ps.isEmpty() && ps.stream().allMatch(cur::inside)) {
            if (ps.stream().noneMatch(p -> p.getBoundingBox().intersects(cur.entry.box().inflate(0.3))))
                cur.entry.close(this, cur);
        }
        cur.onTick(tick);

        // Если долго нет прогресса — Голос издевается и намекает на подсказку.
        long idle = tick - Math.max(cur.lastProgress, cur.activatedAt);
        if (idle > 20 * 60 * 6 && tick - lastIdleTaunt > 20 * 60 * 5 && !voice.busy()) {
            lastIdleTaunt = tick;
            voice.sayGroup("idle");
        }
    }

    /** Голос обращает внимание на шлюзы с цифрами пароля финала. */
    private void checkDigitConnectors(List<ServerPlayer> ps) {
        for (int i = 0; i + 1 < rooms.size(); i++) {
            int k = Layout.digitConnector(i, rooms.size());
            if (k < 0 || digitsAnnounced.contains(k)) continue;
            Room a = rooms.get(i);
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(a.at(a.sx, 0, a.exitZ - 2), a.at(a.sx + Layout.GAP, 5, a.exitZ + 3));
            for (ServerPlayer p : ps)
                if (box.contains(p.position())) {
                    digitsAnnounced.add(k);
                    voice.say("digit.notice." + (k + 1));
                    break;
                }
        }
    }

    public void startGame() {
        data.started = true;
        data.startedAt = level.getGameTime();
        data.setDirty();
    }

    void activate(Room r) {
        r.activated = true;
        r.activatedAt = tick;
        r.lastProgress = tick;
        boolean first = !r.introDone;
        r.introDone = true;
        hudDirty = true;
        r.onActivate(first);
    }

    /** «Где дольше всего тупили»: название и минуты самого долгого испытания, или null. */
    public String slowestRoom() {
        String best = null;
        long bt = 0;
        for (String k : data.roomTime.getAllKeys()) {
            Room r = room(k);
            long t = data.roomTime.getLong(k);
            if (r != null && r.countsAsTrial() && !data.skips.contains(k) && t > bt) { bt = t; best = r.title; }
        }
        return best == null ? null : best + " — " + Math.max(1, bt / 20 / 60) + " мин";
    }

    void solved(Room r) {
        if (r.solved) return;
        r.solved = true;
        data.roomTime.putLong(r.id, tick - r.activatedAt);
        int idx = rooms.indexOf(r);
        if (idx == data.stage) data.stage++;
        data.setDirty();
        r.exit.open(this, r);
        r.entry.setInstant(level, true);
        hudDirty = true;
        TrialComplex.LOG.info("Испытание {} пройдено", r.id);
    }

    // ---------- задачи ----------

    public void later(Room owner, int ticks, Runnable r) {
        tasks.add(new Task(tick + Math.max(1, ticks), owner, owner == null ? 0 : owner.epoch, r));
    }

    private void runTasks() {
        if (tasks.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        tasks.removeIf(t -> {
            if (t.due <= tick) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            if (t.owner != null && t.owner.epoch != t.epoch) continue;
            try {
                t.run.run();
            } catch (Exception e) {
                TrialComplex.LOG.error("Ошибка в задаче комнаты", e);
            }
        }
    }

    // ---------- действия ----------

    public void toCheckpoint(ServerPlayer p) {
        Room r = current();
        if (r == null) r = rooms.get(rooms.size() - 1);
        Vec3 s = r.checkpoint(p);
        p.teleportTo(level, s.x, s.y, s.z, r.spawnYaw(), 0f);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        p.resetFallDistance();
        lastFeet.remove(p.getUUID());
    }

    public void teleport(ServerPlayer p, Vec3 pos, float yaw, float pitch) {
        p.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
        lastFeet.remove(p.getUUID());
    }

    /**
     * Бесшовный сдвиг игрока на вектор (скорость и взгляд сохраняются).
     * ВАЖНО: ServerGamePacketListenerImpl.teleport принимает АБСОЛЮТНУЮ цель и сам считает дельту для пакета.
     */
    public void shift(ServerPlayer p, double dx, double dy, double dz) {
        if (Boolean.getBoolean("trialcomplex.allowVanilla")) // боты mineflayer дважды применяют относительный сдвиг
            p.connection.teleport(p.getX() + dx, p.getY() + dy, p.getZ() + dz, p.getYRot(), p.getXRot());
        else
            p.connection.teleport(p.getX() + dx, p.getY() + dy, p.getZ() + dz, p.getYRot(), p.getXRot(),
                    java.util.EnumSet.allOf(net.minecraft.world.entity.RelativeMovement.class));
        p.resetFallDistance();
        lastFeet.remove(p.getUUID());
    }

    /** Перестроить текущую комнату и начать заново. */
    public void resetRoom(Room r, boolean announce) {
        r.epoch++;
        r.activated = false;
        r.solved = false;
        r.buildAll(level);
        r.onReset();
        if (announce) voice.interrupt(voice.pick("reset"));
        for (ServerPlayer p : players()) if (!bypass(p)) toCheckpoint(p);
        hudDirty = true;
    }

    public void skip(Room r) {
        if (!r.activated) activate(r);
        data.skips.putInt(r.id, 1);
        data.setDirty();
        r.onSkip();
        r.epoch++;
        voice.interrupt(voice.pick("skip"));
        solved(r);
    }

    public void gotoStage(int n) {
        n = Math.max(0, Math.min(n, rooms.size() - 1));
        if (!data.started) startGame();
        voice.stopAll();
        for (int i = 0; i < rooms.size(); i++) {
            Room r = rooms.get(i);
            r.epoch++;
            r.activated = false;
            r.solved = i < n;
            if (i < n) {
                r.introDone = true;
                r.exit.setInstant(level, true);
            }
        }
        data.stage = n;
        data.setDirty();
        resetRoom(rooms.get(n), false);
    }

    /** Следующая подсказка для текущей комнаты. */
    public void hint(ServerPlayer asker) {
        Room r = current();
        if (r == null || !r.activated) {
            voice.say("hint.nothing");
            return;
        }
        int used = data.hints.getInt(r.id);
        if (used >= r.hintCount()) {
            voice.interrupt(voice.pick("hint.none"));
            return;
        }
        used++;
        data.hints.putInt(r.id, used);
        data.setDirty();
        voice.interrupt(voice.pickFor("hint.pre", asker));
        voice.say(r.id + ".hint." + used);
        r.progress();
    }

    // ---------- эффекты ----------

    public void sound(BlockPos pos, SoundEvent s, float vol, float pitch) {
        level.playSound(null, pos, s, SoundSource.BLOCKS, vol, pitch);
    }

    public void particles(ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(type, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    /** Блок, который видит только этот игрок (визуально). */
    public void fakeBlock(ServerPlayer p, BlockPos pos, BlockState s) {
        p.connection.send(new ClientboundBlockUpdatePacket(pos, s));
    }

    public void title(String title, String subtitle) {
        for (ServerPlayer p : players()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        }
    }

    private void sendHud(ServerPlayer p) {
        Room r = current();
        int total = (int) rooms.stream().filter(Room::countsAsTrial).count();
        if (r != null && !r.countsAsTrial() && data.stage > 0) {
            Net.send(p, new Net.Hud(0, 0, r.title, r.objective()));
        } else if (r == null) {
            Net.send(p, new Net.Hud(total, total, "Комплекс пройден", "Поздравляем. Наверное."));
        } else if (data.stage == 0) {
            Net.send(p, new Net.Hud(-1, 0, "", ""));
        } else if (!r.activated) {
            Net.send(p, new Net.Hud(data.stage, total, "Переход", "Идите в следующий зал."));
        } else {
            Net.send(p, new Net.Hud(data.stage, total, r.title, r.objective()));
        }
    }

    private void onJoin(ServerPlayer p) {
        hudDirty = true;
        lastFeet.remove(p.getUUID());
        if (!bypass(p)) p.setGameMode(GameType.ADVENTURE);
        Room r0 = rooms.get(0);
        if (!data.started) {
            r0.placeBeforeStart(p);
        } else if (data.started) {
            toCheckpoint(p);
        }
    }

    // ---------- события Forge ----------

    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void started(ServerStartedEvent e) {
            instance = new Complex(e.getServer());
            instance.init();
            if (Boolean.getBoolean("trialcomplex.autobuild")) DevTools.autobuild(instance);
        }

        @SubscribeEvent
        public static void stopping(ServerStoppingEvent e) {
            instance = null;
        }

        @SubscribeEvent
        public static void serverTick(TickEvent.ServerTickEvent e) {
            if (e.phase == TickEvent.Phase.END && instance != null) instance.onTick();
        }

        @SubscribeEvent
        public static void commands(RegisterCommandsEvent e) {
            PuzzleCommand.register(e.getDispatcher());
        }

        @SubscribeEvent
        public static void login(PlayerEvent.PlayerLoggedInEvent e) {
            if (instance != null && e.getEntity() instanceof ServerPlayer p) instance.onJoin(p);
        }

        @SubscribeEvent
        public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
            if (instance != null && e.getEntity() instanceof ServerPlayer p && !instance.bypass(p))
                instance.later(null, 1, () -> instance.toCheckpoint(p));
        }

        @SubscribeEvent
        public static void use(PlayerInteractEvent.RightClickBlock e) {
            if (instance == null || e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND) return;
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            Room r = instance.current();
            try {
                if (r != null && r.isActive() && r.onUse(p, e.getPos())) e.setCanceled(true);
            } catch (Exception ex) {
                TrialComplex.LOG.error("Ошибка при нажатии в комнате " + r.id, ex);
            }
        }

        @SubscribeEvent
        public static void breakBlock(BlockEvent.BreakEvent e) {
            if (instance != null && e.getPlayer() instanceof ServerPlayer p && !instance.bypass(p)) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void place(BlockEvent.EntityPlaceEvent e) {
            if (instance != null && e.getEntity() instanceof ServerPlayer p && !instance.bypass(p)) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void hurt(LivingAttackEvent e) {
            if (instance != null && e.getEntity() instanceof ServerPlayer) e.setCanceled(true);
        }
    }
}
