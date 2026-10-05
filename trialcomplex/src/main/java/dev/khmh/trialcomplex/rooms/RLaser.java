package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;

import java.util.*;

/**
 * Лазер. Сетка с отражателями (стрелка показывает, куда уйдёт луч). Поворотные — на зелёном основании, ПКМ поворачивает.
 * Фиксированные — на золоте. Луч должен пройти через все кристаллы и попасть в приёмник своего цвета.
 * Уровни сгенерированы tools/levels/laser_gen2.py (с проверкой решения).
 */
public class RLaser extends Room {
    public record Level(String[] grid, String[] emitters, String[] targets) {}

    static final Level[] LEVELS = {
            new Level(new String[]{".......", ".e.n..#", "#......", "#.#....", "#......", "...s...", "....e.."}, new String[]{"W5"}, new String[]{"N1"}),
            new Level(new String[]{".#.......", ".....e...", "##.......", "..E...w..", ".....#.e.", "#........", "....#.C..", "..w...s.C", ".#.#....."}, new String[]{"W7"}, new String[]{"E7"}),
            new Level(new String[]{".#...#.#.", ".n.#.s...", ".C..#..#.", ".sn......", "..C...s..", "....##...", "e#...#...", "#.E.C.N..", "....s..#."}, new String[]{"W1"}, new String[]{"E4"}),
            new Level(new String[]{"#..e.......", "......#.##.", "...w#.....#", "..#........", ".#..#...#..", ".#.wC.S....", "........#..", "..#n.#sws..", "...nC..W...", "#.w........", "...C.....#."}, new String[]{"W2"}, new String[]{"S3"}),
            new Level(new String[]{"..#...#....", "..#..s..S..", ".e#.....C.#", ".C.w....se.", "...C..#.#C.", "...........", "e..W.......", "s..w.#e....", "..#........", "#.......#..", ".#....#...#"}, new String[]{"W3", "N5"}, new String[]{"W7", "S9"}),
            new Level(new String[]{".......#.#.", "..n..#.....", ".w.#C.E..S.", ".#C..#..#C.", "#.........#", "eCW..#.....", "E.e.Ce...s.", ".Ce#....#.n", "....en#..e.", ".##........", ".......#..."}, new String[]{"W1", "N6"}, new String[]{"W7", "N4"}),
    };
    private static final int G0 = 5; // смещение сетки в комнате
    private static final DustParticleOptions[] BEAM = {
            new DustParticleOptions(new Vector3f(1f, 0.1f, 0.1f), 1.2f),
            new DustParticleOptions(new Vector3f(0.2f, 0.5f, 1f), 1.2f)};
    private static final Block[] COLLAR = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE};

    private final Level lv;
    private final int n;
    private final Map<Long, Direction> rot = new HashMap<>(); // ключ — x*1000+z
    private final Set<Long> lit = new HashSet<>();
    private boolean done;
    private final boolean[] hit;

    public RLaser(String id, int level) {
        super(id, "Лазер " + roman(level), LEVELS[level - 1].grid.length + 10, 8, LEVELS[level - 1].grid.length + 10);
        this.lv = LEVELS[level - 1];
        this.n = lv.grid.length;
        this.hit = new boolean[lv.emitters.length];
    }

    static String roman(int n) {
        return new String[]{"", "I", "II", "III", "IV", "V", "VI"}[n];
    }

    private static Direction dir(char c) {
        return switch (Character.toUpperCase(c)) {
            case 'N' -> Direction.NORTH;
            case 'E' -> Direction.EAST;
            case 'S' -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    private char cell(int x, int z) {
        return lv.grid[z].charAt(x);
    }

    private static long key(int x, int z) {
        return x * 1000L + z;
    }

    private BlockPos cellPos(int x, int z) {
        return at(G0 + x, 1, G0 + z);
    }

    /** Блок за краем сетки (излучатель/приёмник). */
    private BlockPos edgePos(String spec) {
        char side = spec.charAt(0);
        int i = Integer.parseInt(spec.substring(1));
        return switch (side) {
            case 'W' -> at(G0 - 1, 1, G0 + i);
            case 'E' -> at(G0 + n, 1, G0 + i);
            case 'N' -> at(G0 + i, 1, G0 - 1);
            default -> at(G0 + i, 1, G0 + n);
        };
    }

    private static BlockState arrow(Direction d) {
        // у магентовой глазури стрелка смотрит в сторону, противоположную FACING
        return Blocks.MAGENTA_GLAZED_TERRACOTTA.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d.getOpposite());
    }

    @Override
    protected void init() {
        resetRot();
    }

    private void resetRot() {
        rot.clear();
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) {
                char c = cell(x, z);
                if ("nesw".indexOf(c) >= 0) rot.put(key(x, z), dir(c));
            }
    }

    @Override
    protected void build(Builder b) {
        Theme.LIGHT.shell(b, sx, sy, sz);
        // площадка сетки
        for (int z = -1; z <= n; z++)
            for (int x = -1; x <= n; x++)
                b.set(G0 + x, 0, G0 + z, (x == -1 || z == -1 || x == n || z == n) ? Blocks.POLISHED_DEEPSLATE : ((x + z) % 2 == 0 ? Blocks.GRAY_CONCRETE : Blocks.BLACK_CONCRETE));
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) {
                char c = cell(x, z);
                BlockPos p = local(cellPos(x, z));
                switch (c) {
                    case '#' -> b.set(p.getX(), 1, p.getZ(), Blocks.OBSIDIAN);
                    case 'C' -> b.set(p.getX(), 1, p.getZ(), Blocks.AMETHYST_BLOCK);
                    case 'N', 'E', 'S', 'W' -> {
                        b.set(p.getX(), 0, p.getZ(), Blocks.GOLD_BLOCK);
                        b.set(p.getX(), 1, p.getZ(), arrow(dir(c)));
                    }
                    case 'n', 'e', 's', 'w' -> {
                        b.set(p.getX(), 0, p.getZ(), Blocks.LIME_CONCRETE);
                        b.set(p.getX(), 1, p.getZ(), arrow(dir(c)));
                    }
                    default -> {}
                }
            }
        for (int i = 0; i < lv.emitters.length; i++) {
            BlockPos e = local(edgePos(lv.emitters[i]));
            Direction out = switch (lv.emitters[i].charAt(0)) { case 'W' -> Direction.EAST; case 'E' -> Direction.WEST; case 'N' -> Direction.SOUTH; default -> Direction.NORTH; };
            b.set(e.getX(), 1, e.getZ(), Builder.facing(Blocks.DISPENSER, out));
            b.set(e.getX(), 0, e.getZ(), COLLAR[i]);
            b.set(e.getX(), 2, e.getZ(), COLLAR[i]);
            BlockPos t = local(edgePos(lv.targets[i]));
            b.set(t.getX(), 1, t.getZ(), Blocks.TARGET);
            b.set(t.getX(), 0, t.getZ(), COLLAR[i]);
            b.set(t.getX(), 2, t.getZ(), COLLAR[i]);
        }
        // легенда
        b.sign(2, 3, 1, Direction.SOUTH, DyeColor.WHITE, "СТРЕЛКА — КУДА", "УЙДЁТ ЛУЧ", "ЗЕЛЁНОЕ ОСНОВАНИЕ", "— ПКМ ПОВЕРНУТЬ");
        b.sign(sx - 3, 3, 1, Direction.SOUTH, DyeColor.WHITE, "ЛУЧ ДОЛЖЕН", "ПРОЙТИ ЧЕРЕЗ", "ВСЕ КРИСТАЛЛЫ", "И ПОПАСТЬ В ЦЕЛЬ");
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        setObjective(lv.emitters.length > 1 ? "Два луча — каждый в мишень своего цвета. Все кристаллы должны гореть."
                : "Направьте луч через все кристаллы в мишень.");
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (done) return false;
        BlockPos l = local(pos);
        int x = l.getX() - G0, z = l.getZ() - G0;
        if (l.getY() != 1 || x < 0 || z < 0 || x >= n || z >= n) return false;
        Direction d = rot.get(key(x, z));
        if (d == null) {
            if ("NESW".indexOf(cell(x, z)) >= 0) sayNow("laser.fixed", p);
            return false;
        }
        d = d.getClockWise();
        rot.put(key(x, z), d);
        set(pos, arrow(d));
        cx.sound(pos, SoundEvents.STONE_BUTTON_CLICK_ON, 0.8f, 1.6f);
        progress();
        return true;
    }

    /** Трассировка одного луча: клетки пути (для частиц) и попадание. */
    private List<BlockPos> trace(int i, Set<Long> litOut) {
        String e = lv.emitters[i];
        char side = e.charAt(0);
        int idx = Integer.parseInt(e.substring(1));
        int x, z;
        Direction d;
        switch (side) {
            case 'W' -> { x = 0; z = idx; d = Direction.EAST; }
            case 'E' -> { x = n - 1; z = idx; d = Direction.WEST; }
            case 'N' -> { x = idx; z = 0; d = Direction.SOUTH; }
            default -> { x = idx; z = n - 1; d = Direction.NORTH; }
        }
        List<BlockPos> path = new ArrayList<>();
        path.add(edgePos(e));
        Set<Long> seen = new HashSet<>();
        hit[i] = false;
        while (x >= 0 && z >= 0 && x < n && z < n) {
            long k = key(x, z) * 4 + d.get2DDataValue();
            if (!seen.add(k)) return path;
            path.add(cellPos(x, z));
            char c = cell(x, z);
            if (c == '#') return path;
            Direction r = rot.get(key(x, z));
            if (r != null) d = r;
            else if ("NESW".indexOf(c) >= 0) d = dir(c);
            else if (c == 'C') litOut.add(key(x, z));
            x += d.getStepX();
            z += d.getStepZ();
        }
        String exit = (x < 0 ? "W" + z : x >= n ? "E" + z : z < 0 ? "N" + x : "S" + x);
        BlockPos out = edgePos(exit);
        path.add(out);
        hit[i] = exit.equals(lv.targets[i]);
        return path;
    }

    @Override
    protected void onTick(long t) {
        if (t % 3 != 0) return;
        Set<Long> nowLit = new HashSet<>();
        boolean all = true;
        for (int i = 0; i < lv.emitters.length; i++) {
            List<BlockPos> path = trace(i, nowLit);
            for (int k = 0; k + 1 < path.size(); k++) {
                var a = net.minecraft.world.phys.Vec3.atCenterOf(path.get(k));
                var c = net.minecraft.world.phys.Vec3.atCenterOf(path.get(k + 1));
                for (int s = 0; s < 4; s++) {
                    var p = a.lerp(c, s / 4.0);
                    level().sendParticles(BEAM[i], p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
            all &= hit[i];
            BlockPos tgt = edgePos(lv.targets[i]);
            BlockState want = (hit[i] ? Blocks.VERDANT_FROGLIGHT : Blocks.TARGET).defaultBlockState();
            if (!level().getBlockState(tgt).is(want.getBlock())) set(tgt, want);
        }
        // кристаллы
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) {
                if (cell(x, z) != 'C') continue;
                boolean on = nowLit.contains(key(x, z));
                BlockState want = (on ? Blocks.PEARLESCENT_FROGLIGHT : Blocks.AMETHYST_BLOCK).defaultBlockState();
                if (!level().getBlockState(cellPos(x, z)).is(want.getBlock())) {
                    set(cellPos(x, z), want);
                    if (on) cx.sound(cellPos(x, z), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                }
                if (!on) all = false;
            }
        if (all && !done) {
            done = true;
            win();
        }
    }

    @Override
    protected void onReset() {
        done = false;
        resetRot();
    }

    /** Перебор поворотов, пока все лучи не попадут и кристаллы не загорятся. */
    @Override
    public String devSolve() {
        List<Long> keys = new ArrayList<>(rot.keySet());
        Map<Long, Direction> backup = new HashMap<>(rot);
        Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        long total = 1L << (2 * keys.size());
        int crystals = 0;
        for (String row : lv.grid) for (char c : row.toCharArray()) if (c == 'C') crystals++;
        for (long m = 0; m < total; m++) {
            for (int i = 0; i < keys.size(); i++) rot.put(keys.get(i), dirs[(int) ((m >> (2 * i)) & 3)]);
            Set<Long> l = new HashSet<>();
            boolean all = true;
            for (int i = 0; i < lv.emitters.length; i++) { trace(i, l); all &= hit[i]; }
            if (all && l.size() == crystals) {
                for (long k : keys) set(cellPos((int) (k / 1000), (int) (k % 1000)), arrow(rot.get(k)));
                return "solved combos=" + m;
            }
        }
        rot.clear();
        rot.putAll(backup);
        return "UNSOLVABLE";
    }

    @Override
    public String debug() {
        StringBuilder sb = new StringBuilder("hit=" + Arrays.toString(hit) + " rot=");
        rot.forEach((k, d) -> sb.append(k / 1000).append(',').append(k % 1000).append('=').append(d.getName().charAt(0)).append(';'));
        return sb.toString();
    }
}
