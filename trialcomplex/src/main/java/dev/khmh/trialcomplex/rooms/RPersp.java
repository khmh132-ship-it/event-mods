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

    record View(Vec3 eye, Vec3 target, String digits, Block block) {}

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
            views.add(new View(new Vec3(4.5, 8 + 1.62, 22.5), new Vec3(16, 8, 9), code, Blocks.WHITE_CONCRETE));
        } else {
            views.add(new View(new Vec3(4.5, 8 + 1.62, 22.5), new Vec3(16, 8, 11), code.substring(0, 2), Blocks.ORANGE_CONCRETE));
            views.add(new View(new Vec3(22.5, 11 + 1.62, 20.5), new Vec3(12, 7, 8), code.substring(2), Blocks.LIGHT_BLUE_CONCRETE));
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
        int[][] towers = level == 1 ? new int[][]{{4, 22, 7}, {22, 22, 7}, {22, 5, 5}, {12, 20, 4}} : new int[][]{{4, 22, 7}, {22, 20, 10}, {4, 5, 6}, {13, 22, 4}};
        for (int[] t : towers) tower(b, t[0], t[2], t[1]);
        RandomSource rnd = RandomSource.create(id.hashCode() * 7L);
        Set<BlockPos> used = new HashSet<>();
        List<Vec3[]> rays = new ArrayList<>();
        for (View vw : views) placeDigits(b, vw, rnd, used, rays);
        // ложные висящие блоки (не на лучах)
        int decoys = 0;
        while (decoys < 90) {
            BlockPos p = new BlockPos(2 + rnd.nextInt(sx - 4), 3 + rnd.nextInt(sy - 5), 2 + rnd.nextInt(sz - 4));
            if (used.contains(p) || !b.get(p.getX(), p.getY(), p.getZ()).isAir()) continue;
            Vec3 c = Vec3.atCenterOf(p);
            boolean near = false;
            for (Vec3[] ray : rays) if (distToSegment(c, ray[0], ray[1]) < 1.3) { near = true; break; }
            if (near) continue;
            b.set(p.getX(), p.getY(), p.getZ(), views.get(rnd.nextInt(views.size())).block);
            used.add(p);
            decoys++;
        }
    }

    private void tower(Builder b, int x, int h, int z) {
        b.fill(x - 1, 1, z - 1, x + 1, h - 1, z + 1, Blocks.PURPUR_PILLAR);
        b.fill(x - 1, h, z - 1, x + 1, h, z + 1, Blocks.PURPUR_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE));
        b.set(x, h, z, Blocks.AMETHYST_BLOCK);
        for (int y = 1; y <= h; y++) b.set(x, y, z + 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        b.set(x, h + 1, z + 2, Blocks.AIR);
    }

    private void placeDigits(Builder b, View vw, RandomSource rnd, Set<BlockPos> used, List<Vec3[]> rays) {
        Vec3 eye = vw.eye;
        Vec3 f = vw.target.subtract(eye).normalize();
        Vec3 right = f.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(f).normalize();
        double D0 = vw.target.subtract(eye).length() * 0.8, s = 0.75;
        int cols = vw.digits.length() * 4 - 1;
        for (int d = 0; d < vw.digits.length(); d++) {
            String g = FONT[vw.digits.charAt(d) - '0'];
            for (int row = 0; row < 5; row++)
                for (int col = 0; col < 3; col++) {
                    if (g.charAt(row * 3 + col) != '1') continue;
                    double cx0 = (d * 4 + col) - (cols - 1) / 2.0, cy0 = 2 - row;
                    Vec3 screen = eye.add(f.scale(D0)).add(right.scale(cx0 * s)).add(up.scale(cy0 * s));
                    Vec3 dir = screen.subtract(eye).normalize();
                    // ищем блок, центр которого ближе всего к лучу, на случайной глубине
                    BlockPos best = null;
                    double bestErr = 9;
                    for (int k = 0; k < 24; k++) {
                        double t = D0 * (0.55 + rnd.nextDouble() * 0.9);
                        Vec3 pt = eye.add(dir.scale(t));
                        BlockPos bp = BlockPos.containing(pt);
                        if (used.contains(bp) || bp.getY() < 2 || bp.getX() < 1 || bp.getZ() < 1 || bp.getX() > sx - 2 || bp.getZ() > sz - 2 || bp.getY() > sy - 3) continue;
                        double err = distToRay(Vec3.atCenterOf(bp), eye, dir) / t;
                        if (err < bestErr) { bestErr = err; best = bp; }
                    }
                    if (best == null) continue;
                    used.add(best);
                    b.set(best.getX(), best.getY(), best.getZ(), vw.block);
                    rays.add(new Vec3[]{eye, Vec3.atCenterOf(best)});
                }
        }
    }

    private static double distToRay(Vec3 p, Vec3 o, Vec3 dir) {
        Vec3 op = p.subtract(o);
        return op.subtract(dir.scale(op.dot(dir))).length();
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 c) {
        Vec3 ab = c.subtract(a);
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / ab.lengthSqr()));
        return p.subtract(a.add(ab.scale(t))).length();
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

    @Override
    public String debug() {
        return "code=" + code + " eye=" + views.get(0).eye;
    }
}
