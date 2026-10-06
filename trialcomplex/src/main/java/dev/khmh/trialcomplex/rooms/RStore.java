package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * «Склад» — сокобан на двоих. Ящики высотой в два блока: через них не перепрыгнуть и из-за них не видно поле.
 * Ящик толкают правой кнопкой, стоя вплотную; тяжёлый (железный) — только вдвоём: второй стоит прямо за первым
 * и тоже жмёт. Ящик нельзя тянуть и нельзя вытолкнуть в проход. Со смотровой вышки видно всё поле.
 * Уровни сгенерированы tools/levels/sokoban_gen.py и проверены решателем.
 */
public class RStore extends Room {
    /** '#' стена, '.' пол, 'x' цель, 'b' лёгкий ящик, 'H' тяжёлый; первый символ строки — '>' у прохода. */
    private static final String[][] LEVELS = {
            {
                    "#........",
                    "#x..#.b..",
                    "#.xx..#b.",
                    ">........",
                    "##...b.#.",
                    "#........",
            },
            {
                    "#.x..#..#",
                    "#....b.b#",
                    "#..H..H..",
                    "##.##...x",
                    "#....x#..",
                    ">.x.....#",
            },
    };
    /** Решения (для проверки ботами): толчки x,z,dx,dz. */
    private static final String[] SOLUTIONS = {
            "4,4,-1,0;6,2,0,1;6,3,-1,0;5,3,-1,0;5,1,-1,0;4,1,0,1;3,4,-1,0;2,4,-1,0;1,4,0,-1;1,3,0,-1;1,2,-1,0;0,2,0,-1;4,3,-1,0;3,3,-1,0;2,3,-1,0;1,3,0,-1;4,2,0,1;4,3,-1,0;3,3,-1,0;2,3,0,-1",
            "2,2,-1,0;1,2,0,1;5,2,-1,0;6,1,0,1;4,1,-1,0;3,1,-1,0;1,3,0,1;4,2,1,0;2,1,-1,0;1,1,0,-1;1,4,0,1;5,2,0,1;5,3,-1,0;6,2,0,1;6,3,1,0;4,3,0,1",
    };
    private static final int GX = 5, GZ = 3;
    private static final BlockState FLOOR = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState TARGET = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final BlockState WALL = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();

    private final int level, W, H;
    private final String[] grid;
    private int gapZ;
    /** Клетка → 'b' или 'H'. */
    private final Map<Long, Character> crates = new HashMap<>();
    private boolean done;
    private long heavyAt = -100, heavyNagAt = -1000;
    private UUID heavyBy;
    private long heavyCell;
    private int heavyDx, heavyDz;

    public RStore(String id, int level) {
        super(id, "Склад " + RLaser.roman(level), LEVELS[level - 1][0].length() - 1 + 9, 10, LEVELS[level - 1].length + 6);
        this.level = level;
        this.grid = LEVELS[level - 1];
        this.W = grid[0].length() - 1;
        this.H = grid.length;
        for (int z = 0; z < H; z++) if (grid[z].charAt(0) == '>') gapZ = z;
    }

    private char cell(int x, int z) {
        return grid[z].charAt(x + 1);
    }

    private static long key(int x, int z) {
        return x * 1000L + z;
    }

    private boolean inGrid(int x, int z) {
        return x >= 0 && z >= 0 && x < W && z < H;
    }

    private boolean isWall(int x, int z) {
        return !inGrid(x, z) || cell(x, z) == '#';
    }

    private boolean isTarget(int x, int z) {
        return inGrid(x, z) && cell(x, z) == 'x';
    }

    private int targetCount() {
        int n = 0;
        for (int z = 0; z < H; z++) for (int x = 0; x < W; x++) if (isTarget(x, z)) n++;
        return n;
    }

    private BlockPos resetButton() {
        return at(2, 2, 1);
    }

    @Override
    protected void build(Builder b) {
        Theme.VAULT.shell(b, sx, sy, sz);
        // пол склада и бортик
        for (int z = -1; z <= H; z++)
            for (int x = -1; x <= W; x++) {
                boolean border = x < 0 || z < 0 || x >= W || z >= H;
                boolean gap = x == -1 && z == gapZ;
                int bx = GX + x, bz = GZ + z;
                if (gap) { b.set(bx, 0, bz, Blocks.YELLOW_CONCRETE); continue; }
                if (border || cell(x, z) == '#') {
                    b.set(bx, 0, bz, WALL); b.set(bx, 1, bz, WALL); b.set(bx, 2, bz, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
                    continue;
                }
                b.set(bx, 0, bz, FLOOR);
            }
        // целевые клетки
        for (int z = 0; z < H; z++) for (int x = 0; x < W; x++) if (isTarget(x, z)) b.set(GX + x, 0, GZ + z, TARGET);
        for (int z = 0; z < H; z++)
            for (int x = 0; x < W; x++) {
                char c = cell(x, z);
                if (c == 'b' || c == 'H') drawCrate(b, x, z, c);
            }
        // проход
        int sz2 = gapZ + 1 < H ? gapZ + 1 : gapZ - 1;
        b.sign(GX - 2, 1, GZ + sz2, Direction.WEST, DyeColor.YELLOW, "", "ВХОД", "НА СКЛАД", gapZ + 1 < H ? "<<<" : ">>>");
        // свет над полем
        for (int z = 0; z < H; z += 3) for (int x = 1; x < W; x += 3) b.set(GX + x, sy - 1, GZ + z, Blocks.PEARLESCENT_FROGLIGHT);
        // смотровая вышка у южной стены западного холла, лестница с севера
        int pz = sz - 3;
        b.fill(1, 1, pz, 2, 5, sz - 2, Blocks.STRIPPED_SPRUCE_WOOD);
        b.fill(1, 6, pz, 2, 6, sz - 2, Blocks.AIR);
        for (int y = 1; y <= 5; y++) b.set(1, y, pz - 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        for (int z = pz - 1; z <= sz - 2; z++) b.set(2, 6, z, Blocks.GLASS_PANE);
        b.set(1, 7, sz - 2, Blocks.LANTERN);
        b.sign(2, 3, pz - 1, Direction.NORTH, DyeColor.WHITE, "", "СМОТРОВАЯ", "", "");
        // сброс
        b.set(2, 2, 0, Blocks.REDSTONE_BLOCK);
        b.set(2, 2, 1, Builder.wallButton(Blocks.STONE_BUTTON, Direction.SOUTH));
        b.sign(2, 3, 1, Direction.SOUTH, DyeColor.ORANGE, "", "СБРОС", "СКЛАДА", "");
        b.sign(3, 3, 1, Direction.SOUTH, DyeColor.WHITE, "ПКМ ПО ЯЩИКУ —", "ТОЛКНУТЬ.", "ЖЕЛЕЗНЫЙ —", "ТОЛЬКО ВДВОЁМ");
        // декор: стеллажи у стен холла
        for (int z = 1; z < sz - 1; z += 3) if (Math.abs(z - entryZ) > 1 && z < pz - 2) b.set(1, 1, z, Blocks.BARREL);
    }

    private void drawCrate(Builder b, int x, int z, char type) {
        BlockState[] s = crateStates(type, isTarget(x, z));
        b.set(GX + x, 1, GZ + z, s[0]);
        b.set(GX + x, 2, GZ + z, s[1]);
    }

    private BlockState[] crateStates(char type, boolean onTarget) {
        if (type == 'H')
            return new BlockState[]{Blocks.IRON_BLOCK.defaultBlockState(), onTarget ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState()};
        BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP);
        return new BlockState[]{barrel, onTarget ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : barrel};
    }

    private void drawCell(int x, int z) {
        Character c = crates.get(key(x, z));
        if (c == null) {
            setL(GX + x, 1, GZ + z, Blocks.AIR.defaultBlockState());
            setL(GX + x, 2, GZ + z, Blocks.AIR.defaultBlockState());
        } else {
            BlockState[] s = crateStates(c, isTarget(x, z));
            setL(GX + x, 1, GZ + z, s[0]);
            setL(GX + x, 2, GZ + z, s[1]);
        }
    }

    private void load() {
        for (long k : new ArrayList<>(crates.keySet())) { crates.remove(k); drawCell((int) (k / 1000), (int) (k % 1000)); }
        for (int z = 0; z < H; z++)
            for (int x = 0; x < W; x++) {
                char c = cell(x, z);
                if (c == 'b' || c == 'H') crates.put(key(x, z), c);
                drawCell(x, z);
            }
        // восточный бортик снова закрыт
        for (int z = -1; z <= H; z++) {
            setL(GX + W, 1, GZ + z, WALL);
            setL(GX + W, 2, GZ + z, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState());
        }
        done = false;
        heavyBy = null;
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        load();
        setObjective("Затолкайте все ящики на золото. ПКМ по ящику — толкнуть. Железные — только вдвоём, друг за другом.");
    }

    /** Клетка поля, в которой стоит игрок (x=-1 — проход). */
    private int[] playerCell(ServerPlayer p) {
        BlockPos l = local(p.blockPosition());
        return new int[]{l.getX() - GX, l.getZ() - GZ};
    }

    private boolean playerIn(int x, int z) {
        AABB box = new AABB(at(GX + x, 1, GZ + z)).expandTowards(0, 1, 0);
        for (ServerPlayer p : players()) if (p.getBoundingBox().intersects(box)) return true;
        return false;
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (pos.equals(resetButton())) {
            if (!done) resetCrates(p);
            return false;
        }
        if (done) return false;
        BlockPos l = local(pos);
        int x = l.getX() - GX, z = l.getZ() - GZ;
        if (l.getY() < 1 || l.getY() > 2 || !crates.containsKey(key(x, z))) return false;
        int[] pc = playerCell(p);
        int dx = x - pc[0], dz = z - pc[1];
        char type = crates.get(key(x, z));
        // толкать можно только по прямой: вплотную, а у железного второй может стоять через клетку
        boolean straight = dx == 0 || dz == 0;
        int dist = Math.abs(dx) + Math.abs(dz);
        if (!straight || dist < 1 || dist > (type == 'H' ? 2 : 1)) return true;
        dx = Integer.signum(dx); dz = Integer.signum(dz);
        int nx = x + dx, nz = z + dz;
        if (isWall(nx, nz) || crates.containsKey(key(nx, nz)) || playerIn(nx, nz)) {
            cx.sound(pos, SoundEvents.CHEST_LOCKED, 0.7f, 0.8f);
            return true;
        }
        if (type == 'H') {
            long t = now();
            boolean partner = heavyBy != null && !heavyBy.equals(p.getUUID()) && t - heavyAt <= 30
                    && heavyCell == key(x, z) && heavyDx == dx && heavyDz == dz;
            if (!partner) {
                heavyBy = p.getUUID(); heavyAt = t; heavyCell = key(x, z); heavyDx = dx; heavyDz = dz;
                cx.sound(pos, SoundEvents.IRON_DOOR_CLOSE, 0.8f, 0.6f);
                if (t - heavyNagAt > 400 && players().size() > 1) { heavyNagAt = t; sayNow("store.heavy", p); }
                return true;
            }
            // оба должны стоять в линию за ящиком: один вплотную, второй — сразу за ним
            boolean near = false, far = false;
            for (ServerPlayer q : players()) {
                int[] qc = playerCell(q);
                if (qc[0] == x - dx && qc[1] == z - dz) near = true;
                else if (qc[0] == x - 2 * dx && qc[1] == z - 2 * dz) far = true;
            }
            heavyBy = null;
            if (!near || !far) {
                cx.sound(pos, SoundEvents.CHEST_LOCKED, 0.7f, 0.6f);
                if (t - heavyNagAt > 200) { heavyNagAt = t; sayNow("store.line", p); }
                return true;
            }
        }
        crates.remove(key(x, z));
        crates.put(key(nx, nz), type);
        drawCell(x, z);
        drawCell(nx, nz);
        cx.sound(at(GX + nx, 1, GZ + nz), type == 'H' ? SoundEvents.ANVIL_PLACE : SoundEvents.PISTON_EXTEND, type == 'H' ? 0.5f : 0.8f, type == 'H' ? 0.7f : 1.1f);
        if (isTarget(nx, nz)) cx.sound(at(GX + nx, 2, GZ + nz), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.3f);
        progress();
        checkWin();
        if (!done && !isTarget(nx, nz) && (isWall(nx - 1, nz) || isWall(nx + 1, nz)) && (isWall(nx, nz - 1) || isWall(nx, nz + 1))) {
            fail();
            later(15, () -> sayNow("store.stuck", p));
        }
        return true;
    }

    private void checkWin() {
        int on = 0;
        for (long k : crates.keySet()) if (isTarget((int) (k / 1000), (int) (k % 1000))) on++;
        if (on < targetCount()) return;
        done = true;
        // восточный бортик опускается — выход к двери
        for (int z = 0; z < H; z++) {
            setL(GX + W, 1, GZ + z, Blocks.AIR.defaultBlockState());
            setL(GX + W, 2, GZ + z, Blocks.AIR.defaultBlockState());
        }
        win();
    }

    private void resetCrates(ServerPlayer by) {
        for (ServerPlayer p : players()) {
            int[] c = playerCell(p);
            if (inGrid(c[0], c[1])) cx.teleport(p, spawn(), spawnYaw(), 0);
        }
        load();
        fail();
        sayNow("store.reset", by);
    }

    @Override
    protected void onReset() {
        crates.clear();
        done = false;
        heavyBy = null;
    }

    @Override
    public String devSolve() {
        // все ящики — на цели
        List<Long> targets = new ArrayList<>();
        for (int z = 0; z < H; z++) for (int x = 0; x < W; x++) if (isTarget(x, z)) targets.add(key(x, z));
        List<Character> types = new ArrayList<>(crates.values());
        for (long k : new ArrayList<>(crates.keySet())) { crates.remove(k); drawCell((int) (k / 1000), (int) (k % 1000)); }
        for (int i = 0; i < targets.size() && i < types.size(); i++) {
            long k = targets.get(i);
            crates.put(k, types.get(i));
            drawCell((int) (k / 1000), (int) (k % 1000));
        }
        checkWin();
        return "ok";
    }

    @Override
    public String debug() {
        StringBuilder sb = new StringBuilder("done=" + done + " gx=" + GX + " gz=" + GZ + " gap=" + gapZ + " sol=" + SOLUTIONS[level - 1] + " crates=");
        for (var e : crates.entrySet()) sb.append(e.getKey() / 1000).append(',').append(e.getKey() % 1000).append(e.getValue()).append(';');
        return sb.toString();
    }
}
