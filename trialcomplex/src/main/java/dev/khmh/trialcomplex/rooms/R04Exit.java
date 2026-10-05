package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Palette;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 4. Выход. Бесконечный коридор: заметили аномалию — разворачивайтесь, нет — идите дальше.
 * Пять верных решений подряд. Переходы между витками — бесшовная телепортация между одинаковыми
 * Z-образными проходами (группа B = группа A + T).
 *
 * Карта (локально, вид сверху, x → восток, z → юг):
 *   D  x1..8   z1..4    — «хвост» коридора (вход в комнату)
 *   A1 x9..12  z1..4,  PA x9..12 z5..11,  A2 x9..12 z12..15
 *   C  x13..36 z12..15  — сам коридор
 *   B1 x37..40 z12..15, PB x37..40 z16..22, B2 x37..40 z23..26
 *   S  x41..48 z23..26  — «начало» следующего коридора (выход из комнаты)
 */
public class R04Exit extends Room {
    static final int TARGET = 5;
    private static final int TX = 28, TZ = 11; // B = A + T
    private static final int C0 = 13, C1 = 36, Z0 = 12;
    private static final double TRIG_A = 8.5, TRIG_B = 19.5;

    enum Anomaly { NONE, LIGHT_OFF, LIGHT_COLOR, DOOR_GONE, EXTRA_DOOR, POSTER, BENCH_GONE, TILE, TYPO, VENT_OPEN,
        PLANT, LOW_CEILING, EXTINGUISHER_MOVED, BELL, ONLY_ONE_SEES }

    private final RandomSource rnd = RandomSource.create();
    private int count;
    private Anomaly anomaly = Anomaly.NONE, lastAnomaly = Anomaly.NONE;
    private boolean enteredFromWest = true, seenMid, done, fakeRevealed, finished;
    private int loops;
    private UUID fakeViewer;
    private final List<BlockPos> fakePositions = new ArrayList<>();
    private final Map<UUID, Vec3> lastPos = new HashMap<>();

    public R04Exit() {
        super("r04", "Выход", 50, 6, 28);
        this.entryZ = 3;
        this.exitZ = 25;
    }

    // ---------- постройка ----------

    @Override
    protected void build(Builder b) {
        b.fill(0, 0, 0, sx - 1, sy - 1, sz - 1, Palette.of(Blocks.LIGHT_GRAY_CONCRETE, 3, Blocks.STONE_BRICKS, 1));
        // C и его копии: D = C[29..36] − T, S = C[13..20] + T
        corridor(b, C0, C1, 0, 0);
        corridor(b, 29, 36, -TX, -TZ);
        corridor(b, 13, 20, TX, TZ);
        // Z-проходы
        zGroup(b, 0, 0);
        zGroup(b, TX, TZ);
        applyAnomaly(b);
        signs(b.level);
    }

    /** Отрезок коридора [u0..u1] (координата C), сдвинутый на (dx,dz). Отделка зависит только от u. */
    private void corridor(Builder b, int u0, int u1, int dx, int dz) {
        for (int u = u0; u <= u1; u++) {
            int x = u + dx;
            for (int z = Z0; z <= Z0 + 3; z++) {
                int zz = z + dz;
                boolean border = z == Z0 || z == Z0 + 3;
                b.set(x, 0, zz, border ? Blocks.GRAY_CONCRETE : ((u + z) % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE));
                for (int y = 1; y <= 4; y++) b.set(x, y, zz, Blocks.AIR);
                b.set(x, 5, zz, u % 4 == 3 && !border ? Blocks.SEA_LANTERN : Blocks.SMOOTH_QUARTZ);
            }
            for (int wz : new int[]{Z0 - 1, Z0 + 4}) {
                boolean pillar = u % 6 == 0;
                for (int y = 1; y <= 4; y++) {
                    BlockState s = pillar ? Builder.axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y)
                            : (y == 1 || y == 4) ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
                    b.set(x, y, wz + dz, s);
                }
            }
            // вентиляция
            if (u % 8 == 5) b.set(x, 4, Z0 + dz, Builder.trapdoor(Blocks.IRON_TRAPDOOR, Direction.SOUTH, true, false));
            // двери на северной стене
            if (u == 18 || u == 24 || u == 30) door(b, x, Z0 - 1 + dz, Direction.SOUTH);
            // плакаты на южной стене
            if (u == 21 || u == 22) {
                b.set(x, 2, Z0 + 4 + dz, u == 21 ? Blocks.BLUE_CONCRETE : Blocks.LIGHT_BLUE_CONCRETE);
                b.set(x, 3, Z0 + 4 + dz, u == 21 ? Blocks.WHITE_CONCRETE : Blocks.BLUE_CONCRETE);
            }
            if (u == 33 || u == 34) {
                b.set(x, 2, Z0 + 4 + dz, u == 33 ? Blocks.ORANGE_CONCRETE : Blocks.YELLOW_CONCRETE);
                b.set(x, 3, Z0 + 4 + dz, Blocks.YELLOW_CONCRETE);
            }
            // огнетушитель
            if (u == 27) extinguisher(b, x, dz, false);
            // скамейка
            if (u == 25 || u == 26) b.set(x, 1, Z0 + 3 + dz, Builder.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
            // растение
            if (u == 32) b.set(x, 1, Z0 + dz, Blocks.POTTED_FERN);
            // правила
            if (u == 14) b.sign(x, 2, Z0 + dz, Direction.SOUTH, DyeColor.YELLOW, "ВИДИШЬ АНОМАЛИЮ —", "ВОЗВРАЩАЙСЯ.", "НЕТ АНОМАЛИЙ —", "ИДИ ДАЛЬШЕ.");
            if (u == 35) b.sign(x, 2, Z0 + 3 + dz, Direction.NORTH, DyeColor.YELLOW, "ВИДИШЬ АНОМАЛИЮ —", "ВОЗВРАЩАЙСЯ.", "НЕТ АНОМАЛИЙ —", "ИДИ ДАЛЬШЕ.");
        }
    }

    private void door(Builder b, int x, int z, Direction facing) {
        b.set(x, 1, z, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        b.set(x, 2, z, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        b.set(x, 3, z, Blocks.POLISHED_ANDESITE);
    }

    private void extinguisher(Builder b, int x, int dz, boolean typo) {
        b.set(x, 2, Z0 + 4 + dz, Blocks.RED_CONCRETE);
        b.set(x, 3, Z0 + 4 + dz, Blocks.RED_TERRACOTTA);
        b.sign(x, 1, Z0 + 3 + dz, Direction.NORTH, DyeColor.RED, "", typo ? "ОГНЕТУПИТЕЛЬ" : "ОГНЕТУШИТЕЛЬ", "", "");
    }

    /** Угол 1, проход, угол 2 (вариант A при dx=dz=0). */
    private void zGroup(Builder b, int dx, int dz) {
        for (int x = 9; x <= 12; x++)
            for (int z = 1; z <= 15; z++) {
                int X = x + dx, Z = z + dz;
                b.set(X, 0, Z, (x == 9 || x == 12) ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE);
                for (int y = 1; y <= 4; y++) b.set(X, y, Z, Blocks.AIR);
                boolean lamp = (x == 10 || x == 11) && (z == 2 || z == 8 || z == 14);
                b.set(X, 5, Z, lamp ? Blocks.SEA_LANTERN : Blocks.SMOOTH_QUARTZ);
            }
        // стены прохода (между углами)
        for (int z = 5; z <= 11; z++)
            for (int y = 1; y <= 4; y++) {
                BlockState s = (y == 1 || y == 4) ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
                b.set(8 + dx, y, z + dz, s);
                b.set(13 + dx, y, z + dz, s);
            }
        // торцевые стены углов
        for (int x = 9; x <= 12; x++)
            for (int y = 1; y <= 4; y++) {
                BlockState s = (y == 1 || y == 4) ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
                b.set(x + dx, y, dz, s);
                b.set(x + dx, y, 16 + dz, s);
            }
        for (int z = 1; z <= 4; z++) for (int y = 1; y <= 4; y++) b.set(13 + dx, y, z + dz, Blocks.WHITE_CONCRETE);
        for (int z = 12; z <= 15; z++) for (int y = 1; y <= 4; y++) b.set(8 + dx, y, z + dz, Blocks.WHITE_CONCRETE);
        // стрелки на полу прохода
        for (int z = 6; z <= 10; z += 2) b.set(10 + dx, 0, z + dz, Blocks.CYAN_TERRACOTTA);
    }

    private void signs(net.minecraft.server.level.ServerLevel lvl) {
        Builder b = new Builder(lvl, origin, 1);
        String cur = finished ? "ОТКРЫТ →" : count + " из " + TARGET;
        int resE = resultEast(), resW = resultWest();
        // прибытие с запада (A2, южная стена) и с востока (B1, северная стена)
        b.sign(10, 3, 15, Direction.NORTH, DyeColor.LIME, "", "ВЫХОД", cur, "");
        b.sign(10 + TX, 3, 12, Direction.SOUTH, DyeColor.LIME, "", "ВЫХОД", cur, "");
        // «превью» за триггерами: B2 = A2 + T, A1 = B1 − T
        b.sign(10 + TX, 3, 15 + TZ, Direction.NORTH, DyeColor.LIME, "", "ВЫХОД", finished ? "ОТКРЫТ →" : resE + " из " + TARGET, "");
        b.sign(10, 3, 1, Direction.SOUTH, DyeColor.LIME, "", "ВЫХОД", finished ? "ОТКРЫТ →" : resW + " из " + TARGET, "");
    }

    private boolean correctEast() {
        return enteredFromWest ? anomaly == Anomaly.NONE : anomaly != Anomaly.NONE;
    }

    private boolean correctWest() {
        return enteredFromWest ? anomaly != Anomaly.NONE : anomaly == Anomaly.NONE;
    }

    private int resultEast() {
        return correctEast() ? count + 1 : 0;
    }

    private int resultWest() {
        return correctWest() ? count + 1 : 0;
    }

    // ---------- аномалии ----------

    private void applyAnomaly(Builder b) {
        fakePositions.clear();
        switch (anomaly) {
            case NONE, BELL -> {}
            case LIGHT_OFF -> b.set(23, 5, Z0 + 1 + rnd.nextInt(2), Blocks.SMOOTH_QUARTZ);
            case LIGHT_COLOR -> {
                b.set(27, 5, Z0 + 1, Blocks.SHROOMLIGHT);
                b.set(27, 5, Z0 + 2, Blocks.SHROOMLIGHT);
            }
            case DOOR_GONE -> {
                int u = 24;
                b.set(u, 1, Z0 - 1, Blocks.LIGHT_GRAY_CONCRETE);
                b.set(u, 2, Z0 - 1, Blocks.WHITE_CONCRETE);
                b.set(u, 3, Z0 - 1, Blocks.WHITE_CONCRETE);
            }
            case EXTRA_DOOR -> door(b, 28, Z0 + 4, Direction.NORTH);
            case POSTER -> {
                b.set(21, 2, Z0 + 4, Blocks.LIGHT_BLUE_CONCRETE);
                b.set(22, 2, Z0 + 4, Blocks.BLUE_CONCRETE);
            }
            case BENCH_GONE -> {
                b.set(25, 1, Z0 + 3, Blocks.AIR);
                b.set(26, 1, Z0 + 3, Blocks.AIR);
            }
            case TILE -> b.set(20 + rnd.nextInt(10), 0, Z0 + 1 + rnd.nextInt(2), Blocks.POLISHED_ANDESITE);
            case TYPO -> extinguisher(b, 27, 0, true);
            case VENT_OPEN -> b.set(21, 4, Z0, Builder.trapdoor(Blocks.IRON_TRAPDOOR, Direction.SOUTH, true, true));
            case PLANT -> {
                // растение у дальнего конца не в зоне D/S? u=32 — внутри C и в копии D (29..36): меняем только в C
                b.set(32, 1, Z0, Blocks.POTTED_DEAD_BUSH);
            }
            case LOW_CEILING -> {
                for (int z = Z0; z <= Z0 + 3; z++) {
                    b.set(25, 4, z, Blocks.SMOOTH_QUARTZ);
                    b.set(26, 4, z, Blocks.SMOOTH_QUARTZ);
                }
            }
            case EXTINGUISHER_MOVED -> {
                b.set(27, 1, Z0 + 3, Blocks.AIR);
                b.set(27, 2, Z0 + 4, Blocks.WHITE_CONCRETE);
                b.set(27, 3, Z0 + 4, Blocks.WHITE_CONCRETE);
                extinguisher(b, 29, 0, false);
            }
            case ONLY_ONE_SEES -> {
                fakePositions.add(at(22, 2, Z0 + 4));
                fakePositions.add(at(22, 3, Z0 + 4));
            }
        }
    }

    private Anomaly roll() {
        if (loops == 0) return Anomaly.NONE; // первый виток всегда чистый
        if (rnd.nextFloat() < 0.4f) return Anomaly.NONE;
        Anomaly[] all = Anomaly.values();
        Anomaly a;
        do {
            a = all[1 + rnd.nextInt(all.length - 1)];
        } while (a == lastAnomaly || (a == Anomaly.ONLY_ONE_SEES && cx.players().size() < 2));
        return a;
    }

    private void rebuildCorridor() {
        Builder b = new Builder(level(), origin, 5);
        // вернуть реальные блоки тем, кому показывали «фальшивку»
        for (ServerPlayer p : cx.players()) for (BlockPos pos : fakePositions) p.connection.send(new ClientboundBlockUpdatePacket(level(), pos));
        corridor(b, C0, C1, 0, 0);
        applyAnomaly(b);
        if (anomaly == Anomaly.ONLY_ONE_SEES) {
            List<ServerPlayer> ps = cx.players();
            fakeViewer = ps.get(rnd.nextInt(ps.size())).getUUID();
        }
        signs(level());
    }

    // ---------- логика ----------

    @Override
    protected void onActivate(boolean firstTime) {
        if (firstTime) voice().say("r04.intro");
        setObjective("Аномалия — разворачивайтесь. Нет аномалии — идите дальше. " + TARGET + " раз подряд.");
    }

    @Override
    protected void onTick(long t) {
        if (anomaly == Anomaly.ONLY_ONE_SEES && t % 10 == 0) {
            for (ServerPlayer p : cx.players())
                if (p.getUUID().equals(fakeViewer)) for (BlockPos pos : fakePositions) cx.fakeBlock(p, pos, Blocks.MAGENTA_GLAZED_TERRACOTTA.defaultBlockState());
        }
        if (anomaly == Anomaly.BELL && t % 70 == 0) cx.sound(at(24, 2, Z0 - 1), SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 0.8f);

        for (ServerPlayer p : cx.players()) {
            Vec3 now = p.position();
            Vec3 prev = lastPos.put(p.getUUID(), now);
            if (prev == null) continue;
            Vec3 l = now.subtract(origin.getX(), origin.getY(), origin.getZ());
            Vec3 pl = prev.subtract(origin.getX(), origin.getY(), origin.getZ());
            if (l.x >= C0 + 9 && l.x <= C0 + 15 && l.z >= Z0 && l.z <= Z0 + 4) seenMid = true;
            // выход на восток: проход B, движение на юг через середину
            if (inPassage(l, TX) && pl.z < TRIG_B && l.z >= TRIG_B) {
                if (finished) continue;
                cross(p, true);
                return;
            }
            // выход на запад: проход A, движение на север через середину
            if (inPassage(l, 0) && pl.z > TRIG_A && l.z <= TRIG_A) {
                cross(p, false);
                return;
            }
        }
    }

    private static boolean inPassage(Vec3 l, int dx) {
        return l.x >= 9 + dx && l.x < 13 + dx && l.z > 4 + (dx == 0 ? 0 : TZ) && l.z < 12 + (dx == 0 ? 0 : TZ);
    }

    /** Игрок пересёк середину прохода. east — ушёл через восточный конец. */
    private void cross(ServerPlayer trigger, boolean east) {
        boolean decided = seenMid && !finished;
        boolean wasAnomaly = anomaly != Anomaly.NONE;
        Anomaly was = anomaly;
        if (decided) {
            boolean correct = east ? correctEast() : correctWest();
            boolean continued = east == enteredFromWest;
            loops++;
            if (correct) {
                count++;
                progress();
                cx.sound(BlockPos.containing(trigger.position()), SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6f, 1.2f + count * 0.1f);
            } else {
                count = 0;
                fail();
            }
            lastAnomaly = anomaly;
            if (count >= TARGET) {
                finished = true;
                anomaly = Anomaly.NONE;
            } else {
                anomaly = roll();
            }
            // голос
            if (!correct) {
                if (was == Anomaly.ONLY_ONE_SEES && !fakeRevealed) {
                    fakeRevealed = true;
                    voice().interrupt("r04.fake.reveal");
                } else voice().interrupt(voice().pick(continued ? "r04.missed" : "r04.paranoid"));
            } else if (finished) {
                voice().interrupt("r04.done");
            } else if (count == TARGET - 1) {
                voice().say("r04.almost");
            } else if (rnd.nextFloat() < 0.3f) {
                voice().say(voice().pick("r04.ok"));
            }
        }
        // телепорт всей группы: восточный выход → в проход A, западный → в проход B
        double dX = east ? -TX : TX, dZ = east ? -TZ : TZ;
        enteredFromWest = east;
        seenMid = false;
        rebuildCorridor();
        Vec3 target = trigger.position().add(dX, 0, dZ);
        for (ServerPlayer p : cx.players()) {
            if (cx.bypass(p)) continue;
            Vec3 l = p.position().subtract(origin.getX(), origin.getY(), origin.getZ());
            if (inPassage(l, east ? TX : 0)) {
                if (Boolean.getBoolean("trialcomplex.allowVanilla")) // боты-тестеры не понимают относительный телепорт
                    p.connection.teleport(p.getX() + dX, p.getY(), p.getZ() + dZ, p.getYRot(), p.getXRot());
                else // бесшовно: сохраняются скорость и направление взгляда
                    p.connection.teleport(dX, 0, dZ, 0, 0, EnumSet.allOf(RelativeMovement.class));
            } else {
                double back = east ? -1.2 : 1.2;
                cx.teleport(p, target.add(0, 0, back), east ? 0f : 180f, 0f);
                voice().say(voice().pickFor("r04.dragged", p));
            }
            lastPos.remove(p.getUUID());
        }
        if (finished) {
            exit.open(cx, this);
            later(40, this::solve);
            setObjective("Выход открыт. Идите на восток.");
        }
        if (decided && wasAnomaly && !finished) progress();
    }

    @Override
    protected boolean lockEntryWhenAllInside() {
        return true;
    }

    @Override
    protected void onReset() {
        count = 0;
        loops = 0;
        anomaly = Anomaly.NONE;
        lastAnomaly = Anomaly.NONE;
        enteredFromWest = true;
        seenMid = false;
        finished = false;
        fakePositions.clear();
        lastPos.clear();
    }

    @Override
    protected void onSkip() {
        finished = true;
        signs(level());
    }

    @Override
    public String debug() {
        return "anomaly=" + anomaly + " count=" + count + " west=" + enteredFromWest + " seenMid=" + seenMid + " finished=" + finished;
    }

    public AABB corridorBox() {
        return new AABB(v(C0, 1, Z0), v(C1 + 1, 5, Z0 + 4));
    }
}
