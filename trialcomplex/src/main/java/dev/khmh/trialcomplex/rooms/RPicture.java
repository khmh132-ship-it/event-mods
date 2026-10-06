package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * «Рисунок» — японский кроссворд на полу. Числа на табличках: длины закрашенных отрезков в строке/столбце по порядку.
 * ПКМ по плитке: белая → чёрная → серая (пометка «здесь пусто») → белая. Когда чёрные совпадут с ответом, картинка
 * проявится в цвете. Картинки проверены tools/levels/nonogram_check.py: решение единственное и выводится логикой.
 */
public class RPicture extends Room {
    private static final String[][] PICTURES = {
            {       // гриб
                    "..RRRR..",
                    ".RR.RRR.",
                    "RRRRR.RR",
                    "R.RRRRRR",
                    "RRRRRRRR",
                    "..SSSS..",
                    "..S.SS..",
                    ".SSSSSS.",
            },
            {       // домик
                    "....RR....",
                    "...RRRR...",
                    "..RRRRRR..",
                    ".RRRRRRRR.",
                    "RRRRRRRRRR",
                    ".W......W.",
                    ".W.BBB..W.",
                    ".W.B.B.YW.",
                    ".W.BBB..W.",
                    ".WWWWWWWW.",
            },
    };
    private static final Map<Character, Block> COLORS = Map.of('R', Blocks.RED_CONCRETE, 'S', Blocks.BIRCH_PLANKS, 'W', Blocks.WHITE_CONCRETE,
            'B', Blocks.BROWN_CONCRETE, 'Y', Blocks.YELLOW_CONCRETE, 'G', Blocks.LIME_CONCRETE);
    private static final BlockState EMPTY = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState FILLED = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState MARK = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final int GX = 5, GZ = 4;

    private final int level, N;
    private final String[] pic;
    private int[][] state; // 0 пусто, 1 закрашено, 2 пометка
    private boolean done;

    public RPicture(String id, int level) {
        super(id, "Рисунок " + RLaser.roman(level), PICTURES[level - 1].length + GX + 5, 8, PICTURES[level - 1].length + GZ + 3);
        this.level = level;
        this.pic = PICTURES[level - 1];
        this.N = pic.length;
    }

    private boolean filled(int x, int z) {
        return pic[z].charAt(x) != '.';
    }

    private List<Integer> runs(boolean row, int i) {
        List<Integer> out = new ArrayList<>();
        int n = 0;
        for (int k = 0; k < N; k++) {
            boolean f = row ? filled(k, i) : filled(i, k);
            if (f) n++;
            else if (n > 0) { out.add(n); n = 0; }
        }
        if (n > 0) out.add(n);
        if (out.isEmpty()) out.add(0);
        return out;
    }

    private BlockPos resetButton() {
        return at(2, 2, 1);
    }

    @Override
    protected void build(Builder b) {
        Theme.LIGHT.shell(b, sx, sy, sz);
        // рамка поля
        b.fill(GX - 1, 0, GZ - 1, GX + N, 0, GZ + N, Blocks.DARK_OAK_PLANKS);
        for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) b.set(GX + x, 0, GZ + z, EMPTY);
        // каждые 5 клеток — направляющие линии на рамке (как в бумажном кроссворде)
        for (int k = 0; k <= N; k += 5) {
            if (k == 0 || k == N) continue;
            b.set(GX + k, 0, GZ - 1, Blocks.GOLD_BLOCK); b.set(GX + k, 0, GZ + N, Blocks.GOLD_BLOCK);
            b.set(GX - 1, 0, GZ + k, Blocks.GOLD_BLOCK); b.set(GX + N, 0, GZ + k, Blocks.GOLD_BLOCK);
        }
        // подсказки строк — слева (текстом на восток), столбцов — сверху (текстом на юг)
        for (int z = 0; z < N; z++) {
            StringBuilder sb = new StringBuilder();
            for (int r : runs(true, z)) sb.append(sb.length() > 0 ? " " : "").append(r);
            b.standingSign(GX - 1, 1, GZ + z, 12, DyeColor.WHITE, "", sb.toString(), "", "");
        }
        for (int x = 0; x < N; x++) {
            List<Integer> r = runs(false, x);
            String[] lines = new String[4];
            if (r.size() <= 4) for (int i = 0; i < 4; i++) lines[i] = i < r.size() ? String.valueOf(r.get(i)) : "";
            else {
                // больше четырёх чисел — по два в строке
                for (int i = 0; i < 4; i++) {
                    StringBuilder sb = new StringBuilder();
                    for (int k = i * 2; k < Math.min(r.size(), i * 2 + 2); k++) sb.append(sb.length() > 0 ? " " : "").append(r.get(k));
                    lines[i] = sb.toString();
                }
            }
            b.standingSign(GX + x, 1, GZ - 1, 0, DyeColor.WHITE, lines);
        }
        // сброс и правила
        b.set(2, 2, 0, Blocks.REDSTONE_BLOCK);
        b.set(2, 2, 1, Builder.wallButton(Blocks.STONE_BUTTON, Direction.SOUTH));
        b.sign(2, 3, 1, Direction.SOUTH, DyeColor.ORANGE, "", "ОЧИСТИТЬ", "ПОЛЕ", "");
        b.sign(3, 3, 1, Direction.SOUTH, DyeColor.WHITE, "ЧИСЛА — ДЛИНЫ", "ЧЁРНЫХ ОТРЕЗКОВ", "ПО ПОРЯДКУ.", "ПКМ ПО ПЛИТКЕ");
        b.sign(4, 3, 1, Direction.SOUTH, DyeColor.WHITE, "БЕЛАЯ → ЧЁРНАЯ", "→ СЕРАЯ", "(«ТОЧНО ПУСТО»)", "→ БЕЛАЯ");
    }

    private void draw(int x, int z) {
        set(at(GX + x, 0, GZ + z), state[z][x] == 1 ? FILLED : state[z][x] == 2 ? MARK : EMPTY);
    }

    private void clear() {
        state = new int[N][N];
        for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) draw(x, z);
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        done = false;
        clear();
        setObjective("Закрасьте клетки по числам. Числа — длины чёрных отрезков по порядку. ПКМ по плитке: белая → чёрная → серая.");
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (state == null) return false;
        if (pos.equals(resetButton())) {
            if (!done) { clear(); sayNow("picture.reset", p); }
            return false;
        }
        if (done) return false;
        BlockPos l = local(pos);
        int x = l.getX() - GX, z = l.getZ() - GZ;
        if (l.getY() != 0 || x < 0 || z < 0 || x >= N || z >= N) return false;
        state[z][x] = (state[z][x] + 1) % 3;
        draw(x, z);
        cx.sound(pos, state[z][x] == 1 ? SoundEvents.WOOL_PLACE : SoundEvents.WOOL_BREAK, 0.8f, state[z][x] == 2 ? 1.5f : 1f);
        progress();
        check();
        return true;
    }

    private void check() {
        for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) if ((state[z][x] == 1) != filled(x, z)) return;
        done = true;
        // картинка проявляется
        for (int z = 0; z < N; z++) {
            final int row = z;
            later(2 * z, () -> {
                for (int x = 0; x < N; x++) {
                    char c = pic[row].charAt(x);
                    set(at(GX + x, 0, GZ + row), c == '.' ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : COLORS.getOrDefault(c, Blocks.BLACK_CONCRETE).defaultBlockState());
                }
                Vec3 v = Vec3.atCenterOf(at(GX + N / 2, 1, GZ + row));
                level().sendParticles(ParticleTypes.HAPPY_VILLAGER, v.x, v.y, v.z, 12, N / 3.0, 0.2, 0.2, 0);
                cx.sound(at(GX + N / 2, 1, GZ + row), SoundEvents.NOTE_BLOCK_CHIME.value(), 1f, 0.6f + row * 0.1f);
            });
        }
        later(2 * N + 10, this::win);
    }

    @Override
    protected void onReset() {
        state = null;
        done = false;
    }

    @Override
    public String devSolve() {
        if (state == null) return "inactive";
        for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) { state[z][x] = filled(x, z) ? 1 : 0; draw(x, z); }
        check();
        return "ok";
    }

    @Override
    public String debug() {
        StringBuilder sb = new StringBuilder("done=" + done + " gx=" + GX + " gz=" + GZ + " sol=");
        for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) if (filled(x, z)) sb.append(x).append(',').append(z).append(';');
        return sb.toString();
    }
}
