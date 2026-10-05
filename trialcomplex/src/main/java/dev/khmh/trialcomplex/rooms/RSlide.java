package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Пятнашки 3×3 из картины 9×9 на стене. Кнопки двигают плитку в пустое место. Уровень 2 — кнопки разнесены. */
public class RSlide extends Room {
    private static final String[][] PICS = {{
            "LLLLLLLYY",
            "LWWLLLYYO",
            "LLLLLLLYY",
            "LLLPPLLLL",
            "LLPPPPLLL",
            "LPPNPPPLG",
            "GGGNGGGGG",
            "BBBBCBBBB",
            "BCBBBBCBB"}, {
            "KKRRRRRKK",
            "KRRWWRRRK",
            "RRWKWRRRR",
            "RRRWRRKRR",
            "RRRRRKKRR",
            "RKRRRRRRR",
            "RRKKKKKRR",
            "KRRRRRRRK",
            "KKRRRRRKK"}};

    private static Block col(char c) {
        return switch (c) {
            case 'L' -> Blocks.LIGHT_BLUE_CONCRETE;
            case 'W' -> Blocks.WHITE_CONCRETE;
            case 'Y' -> Blocks.YELLOW_CONCRETE;
            case 'O' -> Blocks.ORANGE_CONCRETE;
            case 'P' -> Blocks.PURPLE_CONCRETE;
            case 'N' -> Blocks.BROWN_CONCRETE;
            case 'G' -> Blocks.GREEN_CONCRETE;
            case 'B' -> Blocks.BLUE_CONCRETE;
            case 'C' -> Blocks.CYAN_CONCRETE;
            case 'R' -> Blocks.RED_CONCRETE;
            default -> Blocks.BLACK_CONCRETE;
        };
    }

    private static final int PX = 5, PY = 10; // верхний левый пиксель картины на северной стене
    private final int level;
    private final String[] pic;
    private final int[] tiles = new int[9]; // позиция → номер плитки (8 = пусто)
    private boolean done;
    private final RandomSource rnd = RandomSource.create();
    // кнопки: [0]=вверх,[1]=вниз,[2]=влево,[3]=вправо
    private final BlockPos[] buttons = new BlockPos[4];

    public RSlide(String id, int level) {
        super(id, "Пятнашки " + RLaser.roman(level), 19, 13, 15);
        this.level = level;
        this.pic = PICS[level - 1];
    }

    @Override
    protected void init() {
        if (level == 1) {
            buttons[0] = at(8, 1, 7); buttons[1] = at(9, 1, 7); buttons[2] = at(10, 1, 7); buttons[3] = at(11, 1, 7);
        } else {
            buttons[0] = at(2, 1, 9); buttons[1] = at(3, 1, 9); buttons[2] = at(15, 1, 9); buttons[3] = at(16, 1, 9);
        }
    }

    @Override
    protected void build(Builder b) {
        Theme.MECH.shell(b, sx, sy, sz);
        b.fill(PX - 1, PY - 9, 0, PX + 9, PY + 1, 0, Blocks.DARK_OAK_PLANKS);
        for (int i = 0; i < 9; i++) tiles[i] = i;
        drawAll(b);
        String[] labels = {"ВВЕРХ", "ВНИЗ", "ВЛЕВО", "ВПРАВО"};
        for (int k = 0; k < 4; k++) {
            BlockPos l = local(buttons[k]);
            b.set(l.getX(), 1, l.getZ() - 1, Blocks.POLISHED_BLACKSTONE);
            b.set(l.getX(), 2, l.getZ() - 1, Blocks.POLISHED_BLACKSTONE);
            b.set(l.getX(), 1, l.getZ(), Builder.wallButton(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.SOUTH));
            b.sign(l.getX(), 2, l.getZ(), Direction.SOUTH, DyeColor.YELLOW, "", labels[k], "", "");
        }
        b.sign(1, 4, 1, Direction.SOUTH, DyeColor.WHITE, "КНОПКА ДВИГАЕТ", "ПЛИТКУ В ПУСТОЕ", "МЕСТО В ЭТУ", "СТОРОНУ");
    }

    private void drawAll(Builder b) {
        for (int pos = 0; pos < 9; pos++) drawTile(pos, b);
    }

    private void drawTile(int pos, Builder b) {
        int t = tiles[pos];
        int px = pos % 3, py = pos / 3, tx = t % 3, ty = t / 3;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++) {
                Block bl = t == 8 && !done ? Blocks.BLACK_CONCRETE : col(pic[ty * 3 + r].charAt(tx * 3 + c));
                BlockPos p = at(PX + px * 3 + c, PY - (py * 3 + r), 0);
                if (b != null) b.level.setBlock(p, bl.defaultBlockState(), 2);
                else set(p, bl.defaultBlockState());
            }
    }

    private int gap() {
        for (int i = 0; i < 9; i++) if (tiles[i] == 8) return i;
        return 8;
    }

    /** dir: 0 вверх (плитка снизу едет вверх), 1 вниз, 2 влево (плитка справа едет влево), 3 вправо. */
    private boolean move(int dir) {
        int g = gap(), gx = g % 3, gy = g / 3, from;
        switch (dir) {
            case 0 -> { if (gy == 2) return false; from = g + 3; }
            case 1 -> { if (gy == 0) return false; from = g - 3; }
            case 2 -> { if (gx == 2) return false; from = g + 1; }
            default -> { if (gx == 0) return false; from = g - 1; }
        }
        tiles[g] = tiles[from];
        tiles[from] = 8;
        return true;
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        for (int i = 0; i < 9; i++) tiles[i] = i;
        int last = -1;
        for (int k = 0; k < (level == 1 ? 40 : 80); k++) {
            int d = rnd.nextInt(4);
            if ((d ^ 1) == last) continue; // не отменять предыдущий ход
            if (move(d)) last = d;
        }
        if (solved()) { move(0); move(2); }
        for (int pos = 0; pos < 9; pos++) drawTile(pos, null);
        setObjective(level == 1 ? "Соберите картину. Кнопки двигают плитку в пустое место."
                : "Соберите картину. Кнопки «вверх/вниз» слева, «влево/вправо» справа — по одному на пульт.");
    }

    private boolean solved() {
        for (int i = 0; i < 9; i++) if (tiles[i] != i) return false;
        return true;
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (done) return false;
        for (int k = 0; k < 4; k++)
            if (pos.equals(buttons[k])) {
                int g = gap();
                if (move(k)) {
                    drawTile(g, null);
                    drawTile(gap(), null);
                    cx.sound(at(PX + 4, PY - 4, 1), SoundEvents.PISTON_EXTEND, 0.6f, 1.4f);
                    progress();
                    if (solved()) {
                        done = true;
                        drawTile(8, null);
                        win();
                    }
                } else cx.sound(pos, SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.5f);
            }
        return false;
    }

    @Override
    protected void onReset() {
        done = false;
    }

    @Override
    public String debug() {
        return "tiles=" + java.util.Arrays.toString(tiles);
    }
}
