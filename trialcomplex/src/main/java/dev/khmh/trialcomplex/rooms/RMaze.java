package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Palette;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.util.*;

/**
 * Два лабиринта за стеклом. Рычаг в одном лабиринте открывает дверь того же цвета в ДРУГОМ.
 * Карты: строчные буквы — двери, заглавные — рычаги, управляющие дверью-буквой в соседнем лабиринте.
 * Инвертированные двери (цифры 1–5 = двери a–e наоборот) открыты, пока рычаг ВЫКЛЮЧЕН.
 */
public class RMaze extends Room {
    record Level(String[] a, String[] b) {}

    // проверено tools/levels/maze_check.py (X — выход, S — старт)
    static final Level[] LEVELS = {
            new Level(new String[]{"#############", "S..a........#", "######D####.#", "#.Eb........#", "#.###########", "#..c........X", "#############"},
                    new String[]{"#############", "S.A.d.......#", "###########.#", "#.e.B.......#", "#.###########", "#.C.........X", "#############"}),
            new Level(new String[]{"#############", "S..a.1.....D#", "###########.#", "#.Eb.2......#", "#.###########", "#.fc.3......X", "#############"},
                    new String[]{"#############", "S.A.d.4.....#", "###########.#", "#.e.B.F.....#", "#.###########", "#.C.........X", "#############"}),
            new Level(new String[]{"#############", "S.a.#.3...D.#", "#.#.#.###.#.#", "#.#...#E..#.#", "#.#####.###.#", "#.b........cX", "#############"},
                    new String[]{"#############", "S.A..#.c..#.#", "###.##.##.#.#", "#B..d......1#", "#.##.#####.##", "#.#..C.#....X", "#############"}),
    };
    private static final Block[] COLOR = {Blocks.RED_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS,
            Blocks.LIME_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS};
    private static final Block[] FLOOR = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.YELLOW_CONCRETE,
            Blocks.LIME_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.MAGENTA_CONCRETE};
    private static final int MX = 3; // столбец 0 карты = x=3

    private final Level lv;
    private final boolean[] lever = new boolean[6]; // состояние рычага буквы (A..F)
    private boolean done;

    public RMaze(String id, int level) {
        super(id, "Лабиринты " + RLaser.roman(level), 20, 7, 17);
        this.lv = LEVELS[level - 1];
    }

    private int z0(int maze) {
        return maze == 0 ? 1 : 9;
    }

    private char ch(int maze, int col, int row) {
        return (maze == 0 ? lv.a : lv.b)[row].charAt(col);
    }

    private BlockPos cell(int maze, int col, int row) {
        return at(MX + col, 1, z0(maze) + row);
    }

    @Override
    protected void build(Builder b) {
        Theme.LOGIC.shell(b, sx, sy, sz);
        BlockState leaves = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState leaves2 = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        Palette hedge = Palette.of(leaves, 5, leaves2, 1);
        // перегородка
        b.fill(MX, 1, 8, MX + 12, sy - 2, 8, Blocks.GLASS);
        for (int m = 0; m < 2; m++)
            for (int row = 0; row < 7; row++)
                for (int col = 0; col < 13; col++) {
                    char c = ch(m, col, row);
                    BlockPos p = local(cell(m, col, row));
                    int x = p.getX(), z = p.getZ();
                    b.set(x, 0, z, Palette.of(Blocks.MOSS_BLOCK, 3, Blocks.GRASS_BLOCK, 1).pick(b.rnd));
                    if (c == '#') { b.fill(x, 1, z, x, 3, z, hedge); continue; }
                    if (c >= 'a' && c <= 'f' || c >= '1' && c <= '6') {
                        int k = c >= 'a' ? c - 'a' : c - '1';
                        boolean inverted = c <= '6';
                        BlockState door = COLOR[k].defaultBlockState();
                        b.set(x, 1, z, inverted ? Blocks.AIR.defaultBlockState() : door);
                        b.set(x, 2, z, inverted ? Blocks.AIR.defaultBlockState() : door);
                        b.set(x, 3, z, hedge.pick(b.rnd));
                        b.set(x, 0, z, FLOOR[k]);
                    }
                    if (c >= 'A' && c <= 'F') {
                        int k = c - 'A';
                        b.set(x, 0, z, FLOOR[k]);
                        b.set(x, 1, z, Builder.lever(Direction.NORTH, AttachFace.FLOOR, false));
                    }
                    if (c == 'X') b.set(x, 0, z, Blocks.GOLD_BLOCK);
                    if (c == 'S') b.set(x, 0, z, Blocks.EMERALD_BLOCK);
                }
        // зона финиша
        b.fill(MX + 13, 0, 1, sx - 2, 0, sz - 2, Blocks.SMOOTH_STONE);
        b.sign(1, 3, 7, Direction.EAST, DyeColor.WHITE, "РЫЧАГ ОТКРЫВАЕТ", "ДВЕРЬ ТОГО ЖЕ", "ЦВЕТА В ЧУЖОМ", "ЛАБИРИНТЕ");
        for (int z : new int[]{2, 14}) b.set(1, 2, z, Blocks.LANTERN);
        Arrays.fill(lever, false);
    }

    private void applyDoors() {
        for (int m = 0; m < 2; m++)
            for (int row = 0; row < 7; row++)
                for (int col = 0; col < 13; col++) {
                    char c = ch(m, col, row);
                    boolean normal = c >= 'a' && c <= 'f', inv = c >= '1' && c <= '6';
                    if (!normal && !inv) continue;
                    int k = normal ? c - 'a' : c - '1';
                    // дверь в лабиринте m управляется рычагом k из другого лабиринта (общий массив lever)
                    boolean open = normal ? lever[k] : !lever[k];
                    BlockPos p = cell(m, col, row);
                    BlockState s = open ? Blocks.AIR.defaultBlockState() : COLOR[k].defaultBlockState();
                    if (!level().getBlockState(p).is(s.getBlock())) {
                        set(p, s);
                        set(p.above(), s);
                        cx.sound(p, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, 0.8f, 1.2f);
                    }
                }
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        setObjective("Каждый в свой лабиринт. Рычаг открывает дверь того же цвета у напарника. Оба — к золоту.");
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (done) return false;
        for (int m = 0; m < 2; m++)
            for (int row = 0; row < 7; row++)
                for (int col = 0; col < 13; col++) {
                    char c = ch(m, col, row);
                    if (c < 'A' || c > 'F' || !cell(m, col, row).equals(pos)) continue;
                    int k = c - 'A';
                    BlockState st = level().getBlockState(pos);
                    boolean now = !(st.hasProperty(LeverBlock.POWERED) && st.getValue(LeverBlock.POWERED));
                    lever[k] = now;
                    later(1, this::applyDoors);
                    progress();
                    return false;
                }
        return false;
    }

    @Override
    protected void onTick(long t) {
        if (done || t % 10 != 0) return;
        int need = players().size(), there = 0;
        for (ServerPlayer p : players()) if (local(p.blockPosition()).getX() >= MX + 13) there++;
        if (need > 0 && there >= need) {
            done = true;
            win();
        }
    }

    @Override
    protected void onReset() {
        done = false;
        Arrays.fill(lever, false);
    }

    @Override
    public String debug() {
        return "levers=" + Arrays.toString(lever);
    }
}
