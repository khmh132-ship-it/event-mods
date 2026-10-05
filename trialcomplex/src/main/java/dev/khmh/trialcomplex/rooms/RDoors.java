package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Потерянные двери. Зал 9×9 с четырьмя дверями; каждая дверь через Z-коридор бесшовно приводит
 * обратно в тот же зал через противоположную дверь. Нужно пройти правильную последовательность дверей.
 * Подсказка: 1 — ветер дует из нужной двери; 2 — звон из нужной двери; 3 — ветер из всех, кроме нужной.
 */
public class RDoors extends Room {
    // двери: 0=N, 1=E, 2=S, 3=W. Центр двери (в плоскости стены), наружу o, вдоль середины m.
    private static final int[][] D = {{15, 10}, {20, 15}, {15, 20}, {10, 15}};
    private static final int[][] O = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final int[][] M = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final int[][] T = {{-8, 20}, {-20, -8}, {8, -20}, {20, 8}}; // сдвиг из середины коридора i в середину противоположного
    private static final Block[] FRAME = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE};
    private static final String[] NAME = {"красная", "синяя", "жёлтая", "зелёная"};

    private final int level, steps;
    private final RandomSource rnd = RandomSource.create();
    private int step, correct = -1;
    private boolean finished;
    private long lastChoiceAt = -1000;
    private int lastChoiceDoor = -1;
    private final Map<UUID, double[]> lastUV = new HashMap<>();

    public RDoors(String id, int level) {
        super(id, "Потерянные двери " + RLaser.roman(level), 31, 7, 31);
        this.level = level;
        this.steps = level == 1 ? 5 : 6;
        this.entryZ = 7;
        this.exitZ = 23;
    }

    private int[] cell(int door, int u, int v) {
        return new int[]{D[door][0] + O[door][0] * u + M[door][0] * v, D[door][1] + O[door][1] * u + M[door][1] * v};
    }

    @Override
    protected void build(Builder b) {
        // стены одинаковые везде: после бесшовного переноса в другой коридор ничего не должно «прыгнуть»
        b.fill(0, 0, 0, sx - 1, sy - 1, sz - 1, Blocks.END_STONE_BRICKS);
        // зал
        b.clear(11, 1, 11, 19, 5, 19);
        for (int x = 11; x <= 19; x++)
            for (int z = 11; z <= 19; z++) b.set(x, 0, z, (x + z) % 2 == 0 ? Blocks.PURPUR_BLOCK : Blocks.POLISHED_BLACKSTONE);
        b.fill(11, 6, 11, 19, 6, 19, Blocks.PURPUR_BLOCK);
        // колонна и кольцо ламп прогресса
        b.fill(15, 1, 15, 15, 5, 15, Blocks.PURPUR_PILLAR);
        for (int i = 0; i < 6; i++) { int[] p = ring(i); b.set(p[0], 6, p[1], Blocks.BLACK_CONCRETE); }
        for (int x : new int[]{12, 18}) for (int z : new int[]{12, 18}) b.set(x, 5, z, Blocks.END_ROD);
        // коридоры: проём 3×4, сегменты
        for (int d = 0; d < 4; d++) {
            for (int v = -1; v <= 1; v++) carve(b, d, 0, v);
            for (int u = 1; u <= 3; u++) for (int v = -1; v <= 1; v++) carve(b, d, u, v);
            for (int u = 4; u <= 6; u++) for (int v = -1; v <= 9; v++) carve(b, d, u, v);
            for (int u = 7; u <= 9; u++) for (int v = 7; v <= 9; v++) carve(b, d, u, v);
            // свет: центр середины, центр сегментов 1 и 3 (симметрично)
            light(b, d, 5, 4); light(b, d, 2, 0); light(b, d, 8, 8);
            // цветная рамка двери со стороны зала
            for (int v = -2; v <= 2; v += 4) for (int y = 1; y <= 5; y++) { int[] c = cell(d, 0, v); b.set(c[0], y, c[1], FRAME[d]); }
            for (int v = -1; v <= 1; v++) { int[] c = cell(d, 0, v); b.set(c[0], 5, c[1], FRAME[d]); }
        }
        for (int d = 0; d < 4; d++) decorate(b, d);
        if (level == 4) b.sign(13, 3, 11, net.minecraft.core.Direction.SOUTH, DyeColor.WHITE, "ВЕТЕР. ЗВОН.", "ТИШИНА.", "ПО ОЧЕРЕДИ.", "");
        else b.sign(13, 3, 11, net.minecraft.core.Direction.SOUTH, DyeColor.WHITE, "ИДИТЕ ТУДА,", level == 3 ? "ГДЕ НЕТ ВЕТРА" : level == 2 ? "ОТКУДА ЗВЕНИТ" : "ОТКУДА ДУЕТ", level == 2 ? "" : "ВЕТЕР", "");
    }

    private void carve(Builder b, int d, int u, int v) {
        int[] c = cell(d, u, v);
        b.set(c[0], 0, c[1], u == 0 ? Blocks.PURPUR_BLOCK : Blocks.END_STONE_BRICKS);
        for (int y = 1; y <= 4; y++) b.set(c[0], y, c[1], Blocks.AIR);
        b.set(c[0], 5, c[1], Blocks.PURPUR_BLOCK);
    }

    private void at(Builder b, int d, int u, int v, int y, net.minecraft.world.level.block.state.BlockState s) {
        int[] c = cell(d, u, v);
        b.set(c[0], y, c[1], s);
    }

    /** Одинаковый декор в каждом коридоре (в координатах коридора, поэтому все четыре совпадают при повороте). */
    private void decorate(Builder b, int d) {
        net.minecraft.core.Direction out = net.minecraft.core.Direction.fromDelta(O[d][0], 0, O[d][1]);
        var runner = Blocks.PURPUR_BLOCK.defaultBlockState();
        for (int u = 1; u <= 4; u++) at(b, d, u, 0, 0, runner);
        for (int v = 0; v <= 8; v++) at(b, d, 5, v, 0, runner);
        for (int u = 6; u <= 9; u++) at(b, d, u, 8, 0, runner);
        // пилястры
        var pillar = Blocks.PURPUR_PILLAR.defaultBlockState();
        for (int y = 1; y <= 4; y++) {
            for (int v : new int[]{1, 4}) at(b, d, 7, v, y, pillar);
            for (int v : new int[]{4, 7}) at(b, d, 3, v, y, pillar);
            at(b, d, 4, -2, y, pillar);
        }
        // фонари на ножках у стен
        var lantern = Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
        at(b, d, 5, 1, 4, lantern);
        at(b, d, 5, 7, 4, lantern);
        // хорус в мёртвых углах
        for (int[] c : new int[][]{{6, -1}, {4, 9}}) {
            at(b, d, c[0], c[1], 1, Blocks.CHORUS_PLANT.defaultBlockState().setValue(net.minecraft.world.level.block.ChorusPlantBlock.DOWN, true).setValue(net.minecraft.world.level.block.ChorusPlantBlock.UP, true));
            at(b, d, c[0], c[1], 2, Blocks.CHORUS_FLOWER.defaultBlockState().setValue(net.minecraft.world.level.block.ChorusFlowerBlock.AGE, 5));
        }
        // табличка на внутренней стене середины
        int[] sc = cell(d, 4, 3);
        b.sign(sc[0], 3, sc[1], out, DyeColor.PURPLE, "", "ВЫ ТУТ", "УЖЕ БЫЛИ?", "");
    }

    private void light(Builder b, int d, int u, int v) {
        int[] c = cell(d, u, v);
        b.set(c[0], 5, c[1], Blocks.PEARLESCENT_FROGLIGHT);
    }

    private int[] ring(int i) {
        int[][] r = {{13, 13}, {15, 12}, {17, 13}, {17, 17}, {15, 18}, {13, 17}};
        return r[i];
    }

    private void lamps() {
        for (int i = 0; i < 6; i++) {
            int[] p = ring(i);
            setL(p[0], 6, p[1], (i < step ? Blocks.SHROOMLIGHT : Blocks.BLACK_CONCRETE).defaultBlockState());
        }
    }

    private void pickNext() {
        int prev = correct;
        do correct = rnd.nextInt(4); while (correct == prev);
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        step = 0;
        pickNext();
        lamps();
        setObjective("Найдите правильную последовательность дверей: " + steps + " подряд. " + (level == 4 ? "Подсказка меняется: ветер → звон → тишина." : level == 3 ? "Нужная — где НЕТ ветра." : level == 2 ? "Слушайте." : "Смотрите на ветер."));
    }

    /** Позиция игрока в координатах коридора d (u — наружу, v — вдоль середины). */
    private double[] uv(Vec3 p, int d) {
        double px = p.x - origin.getX() - (D[d][0] + 0.5), pz = p.z - origin.getZ() - (D[d][1] + 0.5);
        return new double[]{px * O[d][0] + pz * O[d][1], px * M[d][0] + pz * M[d][1]};
    }

    @Override
    protected void onTick(long t) {
        // подсказки
        if (!finished) {
            for (int d = 0; d < 4; d++) {
                int mode = level == 4 ? new int[]{1, 2, 3}[step % 3] : level;
                boolean wind = mode == 1 ? d == correct : mode == 3 && d != correct;
                if (wind && t % 3 == 0) {
                    int[] c = cell(d, 2, rnd.nextInt(3) - 1);
                    Vec3 at = v(c[0] + 0.5, 1.3 + rnd.nextDouble() * 2, c[1] + 0.5);
                    level().sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, -O[d][0], 0, -O[d][1], 0.25);
                }
                if (mode == 2 && d == correct && t % 30 == 0) {
                    int[] c = cell(d, 1, 0);
                    level().playSound(null, at(c[0], 2, c[1]), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.5f, 1.2f);
                }
            }
            if (level == 3 && t % 40 == 0) level().playSound(null, at(15, 3, 15), SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 0.3f, 0.6f);
        }
        // переходы
        for (ServerPlayer p : players()) {
            if (cx.bypass(p)) continue;
            Vec3 pos = p.position();
            for (int d = 0; d < 4; d++) {
                double[] now = uv(pos, d);
                if (now[0] < 3.5 || now[0] > 6.5 || now[1] < -1.5 || now[1] > 9.5) continue;
                double[] prev = lastUV.get(p.getUUID());
                if (prev != null && prev[2] == d && prev[1] < 4.0 && now[1] >= 4.0) {
                    if (finished && d == 1) break; // выход открыт — восточный коридор больше не петляет
                    cx.shift(p, T[d][0], 0, T[d][1]);
                    lastUV.remove(p.getUUID());
                    choose(p, d);
                    return;
                }
                lastUV.put(p.getUUID(), new double[]{now[0], now[1], d});
            }
        }
    }

    private void choose(ServerPlayer p, int d) {
        if (finished) return;
        // второй игрок, идущий следом в ту же дверь, — тот же выбор
        if (d == lastChoiceDoor && now() - lastChoiceAt < 200) return;
        lastChoiceDoor = d;
        lastChoiceAt = now();
        if (d == correct) {
            step++;
            progress();
            lamps();
            cx.sound(at(15, 4, 15), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.8f + step * 0.1f);
            if (step >= steps) {
                finished = true;
                exit.open(cx, this);
                setObjective("Выход открыт: синяя дверь (восток), до конца коридора.");
                voice().interrupt(id + ".done", this::solve);
                return;
            }
            pickNext();
        } else {
            fail();
            step = 0;
            lamps();
            pickNext();
            voice().interrupt(voice().pickFor("doors.wrong", p));
        }
    }

    @Override
    protected void onReset() {
        step = 0;
        finished = false;
        lastUV.clear();
        lastChoiceDoor = -1;
    }

    @Override
    protected void onSkip() {
        finished = true;
    }

    @Override
    public String debug() {
        return "step=" + step + " correct=" + correct + " finished=" + finished;
    }
}
