package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.PixelText;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Room;
import dev.khmh.trialcomplex.parts.Keypad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Перспектива: в воздухе висят блоки. Только из одной точки (глаза на вышке) они складываются в цифры кода.
 * Уровень 2 — две точки обзора, у каждой по две цифры; порядок — по цвету.
 */
public class RPersp extends Room {
    private static final String[] FONT = {"111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
            "111100111001111", "111100111101111", "111001001010010", "111101111101111", "111101111001111"};
    private static final BlockState OFF = Blocks.BLACK_CONCRETE.defaultBlockState();

    record View(Vec3 eye, Vec3 target, String digits, Block block, double pitch) {}

    private final int level;
    private final String code;
    private final List<View> views = new ArrayList<>();
    private final Keypad keypad = new Keypad(new BlockPos(9, 5, 1), Direction.SOUTH);
    private String entered = "";
    private boolean locked;

    public RPersp(String id, int level) {
        super(id, "Перспектива " + RLaser.roman(level), 27, 16, 27);
        this.level = level;
        RandomSource r = RandomSource.create(id.hashCode());
        StringBuilder c = new StringBuilder();
        for (int i = 0; i < 4; i++) c.append(r.nextInt(10));
        this.code = c.toString();
        if (level == 1) {
            views.add(new View(new Vec3(4.5, 8 + 1.62, 22.5), new Vec3(16, 8, 9), code, Blocks.WHITE_CONCRETE, 1.0 / 11));
        } else {
            views.add(new View(new Vec3(4.5, 8 + 1.62, 4.5), new Vec3(6, 7, 20), code.substring(0, 2), Blocks.ORANGE_CONCRETE, 1.0 / 9));
            views.add(new View(new Vec3(22.5, 11 + 1.62, 4.5), new Vec3(20, 6, 20), code.substring(2), Blocks.LIGHT_BLUE_CONCRETE, 1.0 / 9));
        }
    }

    @Override
    protected void build(Builder b) {
        Theme.ILLUSION.shell(b, sx, sy, sz);
        // приглушить свет: только часть ламп
        for (int x = 2; x < sx - 2; x += 4) for (int z = 2; z < sz - 2; z += 4) if ((x + z) % 8 != 0) {
            b.set(x, sy - 1, z, Blocks.PURPUR_BLOCK); b.set(x + 1, sy - 1, z, Blocks.PURPUR_BLOCK);
            b.set(x, sy - 1, z + 1, Blocks.PURPUR_BLOCK); b.set(x + 1, sy - 1, z + 1, Blocks.PURPUR_BLOCK);
        }
        // табло + клавиатура на северной стене
        b.fill(1, 8, 0, 17, 14, 0, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.fill(2, 9, 0, 16, 13, 0, OFF);
        PixelText.draw(b.level, b.pos(2, 13, 0), Direction.EAST, "____", Blocks.GRAY_CONCRETE.defaultBlockState(), OFF);
        keypad.build(b);
        // смотровые вышки (настоящие и ложные)
        int[][] towers = level == 1 ? new int[][]{{4, 22, 7}, {22, 22, 7}, {22, 5, 5}, {12, 20, 4}} : new int[][]{{4, 4, 7}, {22, 4, 10}, {13, 14, 4}, {13, 5, 5}};
        for (int[] t : towers) tower(b, t[0], t[2], t[1]);
        RandomSource rnd = RandomSource.create(id.hashCode() * 7L);
        Set<BlockPos> used = new HashSet<>();
        for (View vw : views) backdrop(b, vw);
        for (View vw : views) placeDigits(b, vw, rnd, used);
        // ложные висящие блоки: ни один не попадает в «экран» ни одной точки обзора
        int decoys = 0, tries = 0;
        while (decoys < 110 && tries++ < 20000) {
            BlockPos p = new BlockPos(2 + rnd.nextInt(sx - 4), 3 + rnd.nextInt(sy - 5), 2 + rnd.nextInt(sz - 4));
            if (used.contains(p) || !b.get(p.getX(), p.getY(), p.getZ()).isAir()) continue;
            boolean bad = false;
            for (View vw : views) if (inScreen(vw, Vec3.atCenterOf(p), 2.5) || Vec3.atCenterOf(p).distanceTo(vw.eye) < 4) { bad = true; break; }
            if (bad) continue;
            b.set(p.getX(), p.getY(), p.getZ(), views.get(rnd.nextInt(views.size())).block);
            used.add(p);
            decoys++;
        }
    }

    /** Вышка 3×3; наверху стоять можно только в центре, на аметисте (вокруг — невидимый барьер, вход со стороны лестницы). */
    private void tower(Builder b, int x, int h, int z) {
        b.fill(x - 1, 1, z - 1, x + 1, h - 1, z + 1, Blocks.PURPUR_PILLAR);
        b.fill(x - 1, h, z - 1, x + 1, h, z + 1, Blocks.PURPUR_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE));
        b.set(x, h, z, Blocks.AMETHYST_BLOCK);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (!(dx == 0 && dz == 0) && !(dx == 0 && dz == 1)) b.set(x + dx, h + 1, z + dz, Blocks.BARRIER);
        for (int y = 1; y <= h; y++) b.set(x, y, z + 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        b.set(x, h + 1, z + 2, Blocks.AIR);
    }

    private static Vec3[] basis(View vw) {
        Vec3 f = vw.target.subtract(vw.eye).normalize();
        Vec3 right = f.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(f).normalize();
        return new Vec3[]{f, right, up};
    }

    /** Экранные координаты точки (в пикселях шрифта) для точки обзора; null — если позади. */
    private static double[] project(View vw, Vec3 p) {
        Vec3[] bs = basis(vw);
        Vec3 d = p.subtract(vw.eye);
        double z = d.dot(bs[0]);
        if (z <= -1) return null;
        z = Math.max(z, 0.3);
        return new double[]{d.dot(bs[1]) / z / vw.pitch, d.dot(bs[2]) / z / vw.pitch, z};
    }

    private static boolean inScreen(View vw, Vec3 p, double margin) {
        double[] sp = project(vw, p);
        if (sp == null) return false;
        double hw = (vw.digits.length() * 4 - 1) / 2.0, hh = 2.5;
        double rad = 0.87 / sp[2] / vw.pitch; // угловой «радиус» блока в пикселях
        return Math.abs(sp[0]) < hw + margin + rad && Math.abs(sp[1]) < hh + margin + rad;
    }

    /** Тёмный фон на стенах за цифрами, чтобы из точки обзора их было хорошо видно. */
    private void backdrop(Builder b, View vw) {
        Vec3[] bs = basis(vw);
        double hw = (vw.digits.length() * 4 - 1) / 2.0 + 2, hh = 2.5 + 2;
        for (double cx = -hw; cx <= hw; cx += 0.2)
            for (double cy = -hh; cy <= hh; cy += 0.2) {
                Vec3 dir = bs[0].add(bs[1].scale(cx * vw.pitch)).add(bs[2].scale(cy * vw.pitch)).normalize();
                for (double t = 1; t < 60; t += 0.1) {
                    Vec3 pt = vw.eye.add(dir.scale(t));
                    int x = (int) Math.floor(pt.x), y = (int) Math.floor(pt.y), z = (int) Math.floor(pt.z);
                    if (x <= 0 || y <= 0 || z <= 0 || x >= sx - 1 || y >= sy - 1 || z >= sz - 1) {
                        boolean door = (x <= 0 || x >= sx - 1) && y <= 7;
                        boolean board = z <= 0 && x >= 1 && x <= 17 && y >= 3 && y <= 14;
                        if (!door && !board && x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz) b.set(x, y, z, Blocks.BLACK_CONCRETE);
                        break;
                    }
                }
            }
    }

    /**
     * Цифры из висящих блоков: берём все пустые клетки зала, проецируем силуэт каждого куба на «экран» точки обзора
     * и принимаем только те, что целиком ложатся внутрь маски цифр и закрывают ещё не закрытую её часть.
     */
    private void placeDigits(Builder b, View vw, RandomSource rnd, Set<BlockPos> used) {
        int cols = vw.digits.length() * 4 - 1;
        double hw = cols / 2.0, hh = 2.5, R = 0.1;
        int W = (int) Math.ceil(2 * hw / R), H = (int) Math.ceil(2 * hh / R);
        boolean[][] mask = new boolean[W][H], cov = new boolean[W][H];
        int maskCells = 0;
        for (int d = 0; d < vw.digits.length(); d++) {
            String g = FONT[vw.digits.charAt(d) - '0'];
            for (int row = 0; row < 5; row++)
                for (int col = 0; col < 3; col++) {
                    if (g.charAt(row * 3 + col) != '1') continue;
                    double cx0 = (d * 4 + col) - (cols - 1) / 2.0, cy0 = 2 - row;
                    for (int i = (int) Math.round((cx0 - 0.5 + hw) / R); i < (int) Math.round((cx0 + 0.5 + hw) / R); i++)
                        for (int j = (int) Math.round((cy0 - 0.5 + hh) / R); j < (int) Math.round((cy0 + 0.5 + hh) / R); j++)
                            if (i >= 0 && j >= 0 && i < W && j < H && !mask[i][j]) { mask[i][j] = true; maskCells++; }
                }
        }
        List<int[]> cand = new ArrayList<>();
        for (int x = 2; x <= sx - 3; x++)
            for (int y = 2; y <= sy - 3; y++)
                for (int z = 2; z <= sz - 3; z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    if (used.contains(bp) || !b.get(x, y, z).isAir()) continue;
                    if (Vec3.atCenterOf(bp).distanceTo(vw.eye) < 6) continue;
                    boolean clash = false;
                    for (View o : views) if (o != vw && inScreen(o, Vec3.atCenterOf(bp), 1.5)) clash = true;
                    if (clash) continue;
                    double[] box = silhouette(vw, x, y, z);
                    if (box == null || box[1] < -hw || box[0] > hw || box[3] < -hh || box[2] > hh) continue;
                    double size = Math.max(box[1] - box[0], box[3] - box[2]);
                    if (size < 0.5 || size > 1.3) continue;
                    cand.add(new int[]{x, y, z, (int) Math.round((box[0] + hw) / R), (int) Math.round((box[1] + hw) / R), (int) Math.round((box[2] + hh) / R), (int) Math.round((box[3] + hh) / R), (int) Math.round(size * 100)});
                }
        Collections.shuffle(cand, new Random(rnd.nextLong()));
        int covered = 0, placed = 0;
        for (double need : new double[]{0.7, 0.45, 0.25, 0.12, 0.3}) {
            for (int[] c : cand) {
                BlockPos bp = new BlockPos(c[0], c[1], c[2]);
                if (used.contains(bp) || (c[7] < 65 && need != 0.3)) continue;
                int total = 0, inside = 0, fresh = 0;
                for (int i = c[3]; i < c[4]; i++)
                    for (int j = c[5]; j < c[6]; j++) {
                        total++;
                        if (i >= 0 && j >= 0 && i < W && j < H && mask[i][j]) { inside++; if (!cov[i][j]) fresh++; }
                    }
                if (total == 0 || inside < 0.9 * total || fresh < need * total) continue;
                for (int i = Math.max(0, c[3]); i < Math.min(W, c[4]); i++)
                    for (int j = Math.max(0, c[5]); j < Math.min(H, c[6]); j++) if (mask[i][j] && !cov[i][j]) { cov[i][j] = true; covered++; }
                used.add(bp);
                b.set(c[0], c[1], c[2], vw.block);
                placed++;
            }
        }
        dev.khmh.trialcomplex.TrialComplex.LOG.info("{}: цифры {} — {} блоков, покрытие {}%", id, vw.digits, placed, covered * 100 / Math.max(1, maskCells));
    }

    /** Экранный прямоугольник (в пикселях шрифта), в который проецируется куб: {minX, maxX, minY, maxY}. */
    private static double[] silhouette(View vw, int x, int y, int z) {
        double[] box = {1e9, -1e9, 1e9, -1e9};
        for (int i = 0; i < 8; i++) {
            Vec3 c = new Vec3(x + (i & 1), y + ((i >> 1) & 1), z + ((i >> 2) & 1));
            Vec3[] bs = basis(vw);
            Vec3 d = c.subtract(vw.eye);
            double depth = d.dot(bs[0]);
            if (depth < 1) return null;
            double px = d.dot(bs[1]) / depth / vw.pitch, py = d.dot(bs[2]) / depth / vw.pitch;
            box[0] = Math.min(box[0], px); box[1] = Math.max(box[1], px);
            box[2] = Math.min(box[2], py); box[3] = Math.max(box[3], py);
        }
        return box;
    }

    private static double distToRay(Vec3 p, Vec3 o, Vec3 dir) {
        Vec3 op = p.subtract(o);
        return op.subtract(dir.scale(op.dot(dir))).length();
    }

    private void redraw(BlockState on) {
        PixelText.draw(level(), at(2, 13, 0), Direction.EAST, (entered + "____").substring(0, 4), on, OFF);
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        setObjective(level == 1 ? "Найдите точку, откуда висящие блоки складываются в код." : "Две точки обзора: оранжевые цифры — первые, голубые — последние.");
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (locked) return false;
        String k = keypad.keyAt(local(pos));
        if (k == null) return false;
        cx.sound(pos, SoundEvents.NOTE_BLOCK_HAT.value(), 0.8f, 1.5f);
        if (k.equals("C")) entered = "";
        else if (k.equals("OK")) {
            if (entered.equals(code)) {
                locked = true;
                redraw(Blocks.VERDANT_FROGLIGHT.defaultBlockState());
                win();
            } else {
                fail();
                locked = true;
                redraw(Blocks.REDSTONE_BLOCK.defaultBlockState());
                sayNow("persp.wrong", p);
                later(25, () -> { locked = false; entered = ""; redraw(Blocks.OCHRE_FROGLIGHT.defaultBlockState()); });
            }
            return false;
        } else if (entered.length() < 4) entered += k;
        redraw(Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        progress();
        return false;
    }

    @Override
    protected void onReset() {
        entered = "";
        locked = false;
    }

    /** Отладка: ASCII-кадр из каждой точки обзора (в лог сервера). */
    @Override
    public String devSolve() {
        for (View vw : views) {
            Vec3[] bs = basis(vw);
            double hw = (vw.digits.length() * 4 - 1) / 2.0 + 3, hh = 2.5 + 2;
            StringBuilder sb = new StringBuilder("\nVIEW " + vw.digits + "\n");
            for (double t = 0.3; t < 30; t += 0.25) {
                Vec3 pt = vw.eye.add(bs[0].scale(t));
                BlockPos bp = at((int) Math.floor(pt.x), (int) Math.floor(pt.y), (int) Math.floor(pt.z));
                BlockState st = level().getBlockState(bp);
                if (!st.isAir() && !st.is(Blocks.BARRIER)) { sb.append("center hit ").append(st).append(" at t=").append(t).append(" local=").append(local(bp)).append('\n'); break; }
            }
            for (double cy = hh; cy >= -hh; cy -= 0.5) {
                for (double cx = -hw; cx <= hw; cx += 0.25) {
                    Vec3 dir = bs[0].add(bs[1].scale(cx * vw.pitch)).add(bs[2].scale(cy * vw.pitch)).normalize();
                    char ch = '?';
                    for (double t = 0.3; t < 60; t += 0.05) {
                        Vec3 pt = vw.eye.add(dir.scale(t));
                        BlockState st = level().getBlockState(at((int) Math.floor(pt.x), (int) Math.floor(pt.y), (int) Math.floor(pt.z)));
                        if (st.isAir() || st.is(Blocks.LADDER) || st.is(Blocks.BARRIER)) continue;
                        Block bl = st.getBlock();
                        ch = bl == vw.block ? '#' : bl == Blocks.BLACK_CONCRETE ? ' ' : (bl == Blocks.ORANGE_CONCRETE || bl == Blocks.LIGHT_BLUE_CONCRETE || bl == Blocks.WHITE_CONCRETE) ? 'o' : '.';
                        break;
                    }
                    sb.append(ch);
                }
                sb.append('\n');
            }
            dev.khmh.trialcomplex.TrialComplex.LOG.info(sb.toString());
        }
        return code;
    }

    @Override
    public String debug() {
        return "code=" + code + " eye=" + views.get(0).eye;
    }
}
