package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Сапёр на полу. Наступил — открыл клетку: цвет = число мин вокруг (по 8 соседям). Мина — всех на старт, поле новое.
 * ПКМ по скрытой клетке ставит/снимает флажок. Поле генерируется так, чтобы дойти до конца можно было логикой.
 * Уровень 3: цвета видит только тот, кто НЕ стоит на поле.
 */
public class RMines extends Room {
    private static final int F0 = 5; // поле начинается с x=F0
    private static final BlockState HIDDEN = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState FLAG = Blocks.RED_WOOL.defaultBlockState();
    private static final Block[] NUM = {Blocks.WHITE_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIME_CONCRETE,
            Blocks.YELLOW_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.MAGENTA_CONCRETE};

    private final int level, L, W, mines;
    private final RandomSource rnd = RandomSource.create();
    private boolean[][] mine, open, flag;
    private boolean boom, done;
    private final Map<UUID, Boolean> sawField = new HashMap<>();

    public RMines(String id, int level) {
        this(id, level, level);
    }

    /** shown — номер в названии (второе поле заменено «Рисунком»). */
    public RMines(String id, int level, int shown) {
        super(id, "Сапёр " + RLaser.roman(shown), new int[]{0, 10, 14, 12, 16}[level] + 10, 9, new int[]{0, 7, 9, 8, 9}[level] + 2);
        this.level = level;
        this.L = new int[]{0, 10, 14, 12, 16}[level];
        this.W = new int[]{0, 7, 9, 8, 9}[level];
        this.mines = new int[]{0, 11, 24, 16, 30}[level];
    }

    private int z0() {
        return 1;
    }

    private BlockPos tile(int x, int z) {
        return at(F0 + x, 0, z0() + z);
    }

    @Override
    protected void build(Builder b) {
        Theme.MINE.shell(b, sx, sy, sz);
        // поле
        for (int x = -1; x <= L; x++)
            for (int z = 0; z < W; z++)
                b.set(F0 + x, 0, z0() + z, (x == -1 || x == L) ? Blocks.YELLOW_CONCRETE : HIDDEN.getBlock());
        // легенда цветов на северной стене
        String[] names = {"0", "1", "2", "3", "4", "5+"};
        for (int i = 0; i < 6; i++) {
            int x = 3 + i * 2;
            b.set(x, 2, 0, NUM[i]);
            b.set(x, 3, 0, NUM[i]);
            b.sign(x, 4, 1, Direction.SOUTH, DyeColor.WHITE, "", "= " + names[i], "", "");
        }
        b.sign(sx - 4, 4, 1, Direction.SOUTH, DyeColor.YELLOW, "ЦВЕТ = СКОЛЬКО", "МИН ВОКРУГ", "(8 СОСЕДЕЙ)", "ПКМ — ФЛАЖОК");
        // ящики «сапёров»
        for (int z : new int[]{1, sz - 2}) {
            b.set(2, 1, z, Blocks.BARREL);
            b.set(3, 1, z, Blocks.TNT);
            b.set(sx - 3, 1, z, Blocks.BARREL);
        }
    }

    // ---------- генерация с проверкой решаемости ----------

    private void generate() {
        for (int attempt = 0; attempt < 400; attempt++) {
            mine = new boolean[L][W];
            int placed = 0;
            while (placed < mines) {
                int x = 2 + rnd.nextInt(L - 2), z = rnd.nextInt(W);
                if (!mine[x][z]) { mine[x][z] = true; placed++; }
            }
            if (solvable()) break;
        }
        open = new boolean[L][W];
        flag = new boolean[L][W];
        // первые два столбца открыты сразу (с заливкой нулей)
        for (int z = 0; z < W; z++) { reveal(0, z, false); reveal(1, z, false); }
        redrawAll();
    }

    private int count(int x, int z) {
        int c = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int nx = x + dx, nz = z + dz;
                if ((dx != 0 || dz != 0) && nx >= 0 && nz >= 0 && nx < L && nz < W && mine[nx][nz]) c++;
            }
        return c;
    }

    /** Простой решатель: открывает всё, что выводится логикой; true если дошли до последнего столбца. */
    private boolean solvable() {
        boolean[][] op = new boolean[L][W], mk = new boolean[L][W];
        Deque<int[]> q = new ArrayDeque<>();
        for (int z = 0; z < W; z++) { q.add(new int[]{0, z}); q.add(new int[]{1, z}); }
        // открытие с заливкой
        java.util.function.Consumer<int[]> openCell = new java.util.function.Consumer<>() {
            public void accept(int[] c) {
                Deque<int[]> st = new ArrayDeque<>();
                st.add(c);
                while (!st.isEmpty()) {
                    int[] p = st.poll();
                    int x = p[0], z = p[1];
                    if (x < 0 || z < 0 || x >= L || z >= W || op[x][z] || mine[x][z]) continue;
                    op[x][z] = true;
                    if (count(x, z) == 0)
                        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) st.add(new int[]{x + dx, z + dz});
                }
            }
        };
        while (!q.isEmpty()) openCell.accept(q.poll());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int x = 0; x < L; x++)
                for (int z = 0; z < W; z++) {
                    if (!op[x][z]) continue;
                    int n = count(x, z), known = 0;
                    List<int[]> unk = new ArrayList<>();
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++) {
                            int nx = x + dx, nz = z + dz;
                            if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx >= L || nz >= W || op[nx][nz]) continue;
                            if (mk[nx][nz]) known++;
                            else unk.add(new int[]{nx, nz});
                        }
                    if (unk.isEmpty()) continue;
                    if (known == n) { for (int[] u : unk) openCell.accept(u); changed = true; }
                    else if (known + unk.size() == n) { for (int[] u : unk) mk[u[0]][u[1]] = true; changed = true; }
                }
        }
        // путь по открытым клеткам от первого столбца до последнего
        boolean[][] seen = new boolean[L][W];
        Deque<int[]> st = new ArrayDeque<>();
        for (int z = 0; z < W; z++) if (op[0][z]) st.add(new int[]{0, z});
        while (!st.isEmpty()) {
            int[] p = st.poll();
            int x = p[0], z = p[1];
            if (x < 0 || z < 0 || x >= L || z >= W || seen[x][z] || !op[x][z]) continue;
            seen[x][z] = true;
            if (x == L - 1) return true;
            st.add(new int[]{x + 1, z}); st.add(new int[]{x - 1, z}); st.add(new int[]{x, z + 1}); st.add(new int[]{x, z - 1});
        }
        return false;
    }

    private void reveal(int x, int z, boolean draw) {
        Deque<int[]> st = new ArrayDeque<>();
        st.add(new int[]{x, z});
        while (!st.isEmpty()) {
            int[] p = st.poll();
            int cx0 = p[0], cz = p[1];
            if (cx0 < 0 || cz < 0 || cx0 >= L || cz >= W || open[cx0][cz] || mine[cx0][cz]) continue;
            open[cx0][cz] = true;
            flag[cx0][cz] = false;
            if (draw) drawTile(cx0, cz);
            if (count(cx0, cz) == 0)
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) st.add(new int[]{cx0 + dx, cz + dz});
        }
    }

    private BlockState shown(int x, int z) {
        if (open[x][z]) return NUM[count(x, z)].defaultBlockState();
        return flag[x][z] ? FLAG : HIDDEN;
    }

    private void drawTile(int x, int z) {
        // уровень 3: открытые клетки в мире остаются серыми, цвет видят только «зрители» (фальш-блоки)
        BlockState s = shown(x, z);
        if (level == 3 && open[x][z]) s = HIDDEN;
        set(tile(x, z), s);
    }

    private void redrawAll() {
        for (int x = 0; x < L; x++) for (int z = 0; z < W; z++) drawTile(x, z);
        sawField.clear();
    }

    private boolean onField(ServerPlayer p) {
        BlockPos l = local(p.blockPosition());
        return l.getX() >= F0 && l.getX() < F0 + L && l.getZ() >= z0() && l.getZ() < z0() + W;
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        generate();
        setObjective(level == 3 ? "Цвета видит только тот, кто НЕ на поле. Доберитесь до другого края."
                : "Пройдите поле до жёлтой линии на другой стороне. Цвет клетки — число мин рядом.");
    }

    @Override
    protected void onStep(ServerPlayer p, BlockPos floor) {
        if (boom || done || mine == null) return;
        BlockPos l = local(floor);
        int x = l.getX() - F0, z = l.getZ() - z0();
        if (l.getY() != 0) return;
        if (x >= L && x <= L + 3 && z >= -1 && z <= W) {
            checkFinish();
            return;
        }
        if (x < 0 || z < 0 || x >= L || z >= W) return;
        if (mine[x][z]) {
            explode(p, x, z);
            return;
        }
        if (!open[x][z]) {
            reveal(x, z, true);
            cx.sound(floor, SoundEvents.NOTE_BLOCK_HAT.value(), 0.6f, 1.6f);
            progress();
        }
    }

    private void checkFinish() {
        int need = players().size(), there = 0;
        for (ServerPlayer p : players()) {
            BlockPos l = local(p.blockPosition());
            if (l.getX() >= F0 + L) there++;
        }
        if (there >= need) {
            done = true;
            for (int x = 0; x < L; x++) for (int z = 0; z < W; z++) if (mine[x][z]) set(tile(x, z), Blocks.TNT.defaultBlockState());
            win();
        }
    }

    private void explode(ServerPlayer p, int x, int z) {
        boom = true;
        fail();
        BlockPos t = tile(x, z);
        set(t, Blocks.TNT.defaultBlockState());
        level().playSound(null, t, SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
        cx.particles(ParticleTypes.EXPLOSION_EMITTER, Vec3.atCenterOf(t.above()), 1, 0, 0);
        voice().interrupt(voice().pickFor("mines.boom", p));
        later(30, () -> {
            for (ServerPlayer q : players()) if (!cx.bypass(q)) cx.teleport(q, spawn(), -90f, 0f);
            generate();
            boom = false;
        });
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (mine == null || done) return false;
        BlockPos l = local(pos);
        int x = l.getX() - F0, z = l.getZ() - z0();
        if (l.getY() != 0 || x < 0 || z < 0 || x >= L || z >= W || open[x][z]) return false;
        flag[x][z] = !flag[x][z];
        drawTile(x, z);
        cx.sound(pos, SoundEvents.WOOL_PLACE, 1f, 1.2f);
        return true;
    }

    @Override
    protected void onTick(long t) {
        if (level != 3 || mine == null || t % 5 != 0) return;
        for (ServerPlayer p : players()) {
            boolean viewer = !onField(p) && players().stream().anyMatch(q -> q != p && onField(q));
            Boolean was = sawField.put(p.getUUID(), viewer);
            if (viewer) {
                for (int x = 0; x < L; x++) for (int z = 0; z < W; z++) if (open[x][z]) cx.fakeBlock(p, tile(x, z), shown(x, z));
            } else if (Boolean.TRUE.equals(was)) {
                for (int x = 0; x < L; x++) for (int z = 0; z < W; z++) p.connection.send(new ClientboundBlockUpdatePacket(level(), tile(x, z)));
            }
        }
    }

    @Override
    protected void onReset() {
        boom = done = false;
        mine = null;
    }

    @Override
    public String debug() {
        if (mine == null) return "no field";
        StringBuilder sb = new StringBuilder("L=" + L + " W=" + W + " mines=");
        for (int x = 0; x < L; x++) for (int z = 0; z < W; z++) if (mine[x][z]) sb.append(x).append(',').append(z).append(';');
        return sb.toString();
    }
}
