package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Palette;
import dev.khmh.trialcomplex.build.Styles;
import dev.khmh.trialcomplex.game.Gate;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1. Синхрон. Две половины за тонированным стеклом. Сначала просто нажать одновременно,
 * потом — одновременно нажать символ, который видит на экране только один из вас.
 */
public class R01Sync extends Room {
    private static final Block[] SYMBOLS = {
            Blocks.WHITE_GLAZED_TERRACOTTA, Blocks.ORANGE_GLAZED_TERRACOTTA, Blocks.MAGENTA_GLAZED_TERRACOTTA,
            Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, Blocks.YELLOW_GLAZED_TERRACOTTA, Blocks.LIME_GLAZED_TERRACOTTA,
            Blocks.PINK_GLAZED_TERRACOTTA, Blocks.CYAN_GLAZED_TERRACOTTA, Blocks.PURPLE_GLAZED_TERRACOTTA,
            Blocks.BLUE_GLAZED_TERRACOTTA, Blocks.BROWN_GLAZED_TERRACOTTA, Blocks.GREEN_GLAZED_TERRACOTTA,
            Blocks.RED_GLAZED_TERRACOTTA, Blocks.BLACK_GLAZED_TERRACOTTA, Blocks.GRAY_GLAZED_TERRACOTTA,
            Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA};
    private static final int[][] SLOTS_Z = {{1, 3, 5, 7}, {17, 15, 13, 11}}; // [сторона][слот], слева направо для смотрящего
    private static final int[] SCREEN_Z = {4, 14};
    private static final int PANEL_X = 16;
    private static final int ROUNDS = 4; // 0 — просто синхрон, 1..3 — символы

    private record Press(int side, int slot, long t, ServerPlayer p) {}

    private final RandomSource rnd = RandomSource.create();
    private Gate gateN, gateS, finish;
    private int phase; // 0 вступление, 1 ждём расхода, 2 раунды, 3 готово
    private int round;
    private int screenSide;
    private final Block[][] shown = new Block[2][4];
    private Block target;
    private final Press[] pending = new Press[2];
    private long cooldownUntil;
    private boolean splitOnce;
    private int strayTicks;
    private String savedObjective = "";

    public R01Sync() {
        super("r01", "Синхрон", 21, 9, 19);
    }

    @Override
    protected void build(Builder b) {
        Styles.lab(b, sx, sy, sz, Blocks.SEA_LANTERN.defaultBlockState(), Blocks.ORANGE_TERRACOTTA);
        Palette tint = Palette.of(Blocks.TINTED_GLASS);
        // перегородка
        b.fill(5, 1, 9, PANEL_X, sy - 2, 9, tint);
        for (int x = 5; x <= PANEL_X; x += 3) b.fill(x, 1, 9, x, sy - 2, 9, Blocks.POLISHED_DEEPSLATE);
        // стена с кнопками
        b.fill(PANEL_X, 1, 1, PANEL_X, sy - 2, sz - 2, Styles.LAB_WALL);
        b.fill(PANEL_X, 1, 1, PANEL_X, 1, sz - 2, Styles.LAB_TRIM);
        // зона выхода и закрытые части за ней
        b.fill(PANEL_X + 1, 1, 1, sx - 2, sy - 2, 7, Styles.LAB_WALL);
        b.fill(PANEL_X + 1, 1, 11, sx - 2, sy - 2, sz - 2, Styles.LAB_WALL);
        b.fill(PANEL_X + 1, 5, 8, sx - 2, sy - 2, 10, Styles.LAB_WALL);
        for (int x = PANEL_X + 1; x < sx - 1; x++) b.set(x, 0, 9, Blocks.SEA_LANTERN);
        // панели
        for (int side = 0; side < 2; side++) {
            for (int slot = 0; slot < 4; slot++) {
                int z = SLOTS_Z[side][slot];
                b.set(PANEL_X, 2, z, Blocks.POLISHED_BLACKSTONE);
                b.set(PANEL_X - 1, 2, z, Builder.wallButton(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.WEST));
                b.set(PANEL_X, 3, z, Blocks.BLACK_CONCRETE);
            }
            int cz = SCREEN_Z[side];
            b.fill(PANEL_X, 4, cz - 1, PANEL_X, 6, cz + 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
            b.set(PANEL_X, 5, cz, Blocks.BLACK_CONCRETE);
            b.set(PANEL_X, 7, cz, Blocks.CYAN_TERRACOTTA);
            // дорожка к панели
            int midZ = side == 0 ? 4 : 14;
            for (int x = 6; x < PANEL_X; x++) b.set(x, 0, midZ, x % 2 == 0 ? Blocks.CYAN_TERRACOTTA : Blocks.LIGHT_GRAY_CONCRETE);
        }
        b.sign(4, 3, 8, Direction.WEST, DyeColor.WHITE, "", "ЛЕВАЯ", "ПОЛОВИНА", "");
        b.sign(4, 3, 10, Direction.WEST, DyeColor.WHITE, "", "ПРАВАЯ", "ПОЛОВИНА", "");
        gates().forEach(g -> g.setInstant(b.level, g != finish));
    }

    @Override
    protected void init() {
        gates();
    }

    private List<Gate> gates() {
        if (gateN == null) {
            Palette wall = Palette.of(Blocks.WHITE_CONCRETE, 3, Blocks.LIGHT_GRAY_CONCRETE, 1);
            gateN = new Gate(at(5, 1, 1), at(5, sy - 2, 8), wall);
            gateS = new Gate(at(5, 1, 10), at(5, sy - 2, sz - 2), wall);
            finish = new Gate(at(PANEL_X, 1, 8), at(PANEL_X, 4, 10), Palette.of(Blocks.IRON_BLOCK));
        }
        return List.of(gateN, gateS, finish);
    }

    private AABB half(int side) {
        return side == 0 ? new AABB(v(6, 1, 1), v(PANEL_X, sy - 1, 9)) : new AABB(v(6, 1, 10), v(PANEL_X, sy - 1, sz - 1));
    }

    private ServerPlayer playerIn(int side) {
        for (ServerPlayer p : cx.players()) if (half(side).contains(p.position())) return p;
        return null;
    }

    @Override
    protected void onActivate(boolean firstTime) {
        phase = 1;
        if (firstTime) voice().say("r01.intro");
        setObjective("Разойдитесь: один — в левую половину, другой — в правую.");
    }

    @Override
    protected void onTick(long t) {
        if (phase == 1 && !voice().busy()) {
            ServerPlayer a = playerIn(0), b = playerIn(1);
            if (a != null && b != null && a != b) {
                if (a.getBoundingBox().intersects(gateN.box()) || b.getBoundingBox().intersects(gateS.box())) return;
                gateN.close(cx, this);
                gateS.close(cx, this);
                phase = 2;
                progress();
                if (splitOnce) {
                    setObjective(savedObjective);
                } else {
                    splitOnce = true;
                    voice().say("r01.split", () -> setObjective("Нажмите по кнопке ОДНОВРЕМЕННО (не позже полсекунды)."));
                }
            }
        }
        // кто-то вылетел/вернулся в лобби — открываем половины, раунд сохраняется
        if (phase == 2 && cx.players().size() >= 2 && (playerIn(0) == null || playerIn(1) == null)) {
            if (++strayTicks > 40) {
                strayTicks = 0;
                phase = 1;
                savedObjective = objective();
                gateN.open(cx, this);
                gateS.open(cx, this);
                setObjective("Разойдитесь по половинам снова — продолжим с того же места.");
            }
        } else strayTicks = 0;
    }

    private void setupRound() {
        // 4 случайных символа, у каждой стороны в своём порядке
        List<Block> pool = new ArrayList<>(List.of(SYMBOLS));
        Collections.shuffle(pool, new java.util.Random(rnd.nextLong()));
        List<Block> four = new ArrayList<>(pool.subList(0, 4));
        target = four.get(rnd.nextInt(4));
        for (int side = 0; side < 2; side++) {
            Collections.shuffle(four, new java.util.Random(rnd.nextLong()));
            for (int slot = 0; slot < 4; slot++) {
                shown[side][slot] = four.get(slot);
                level().setBlock(at(PANEL_X, 3, SLOTS_Z[side][slot]), sym(four.get(slot)), Block.UPDATE_CLIENTS);
            }
        }
        screenSide = round == 1 ? rnd.nextInt(2) : 1 - screenSide;
        showScreens(false);
        cx.sound(at(PANEL_X, 5, 9), SoundEvents.BEACON_POWER_SELECT, 1f, 1.4f);
    }

    private static BlockState sym(Block b) {
        return Builder.facing(b, Direction.NORTH);
    }

    private void showScreens(boolean blank) {
        for (int side = 0; side < 2; side++) {
            BlockState s = !blank && side == screenSide && target != null ? sym(target) : Blocks.BLACK_CONCRETE.defaultBlockState();
            level().setBlock(at(PANEL_X, 5, SCREEN_Z[side]), s, Block.UPDATE_CLIENTS);
        }
    }

    private void flash(boolean ok) {
        BlockState s = (ok ? Blocks.VERDANT_FROGLIGHT : Blocks.REDSTONE_BLOCK).defaultBlockState();
        for (int side = 0; side < 2; side++) level().setBlock(at(PANEL_X, 5, SCREEN_Z[side]), s, Block.UPDATE_CLIENTS);
        cx.sound(at(PANEL_X, 4, 9), ok ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_BASS.value(), 1f, ok ? 1.2f : 0.5f);
        later(20, () -> showScreens(round == 0));
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (phase != 2) return false;
        BlockPos l = local(pos);
        if (l.getX() != PANEL_X - 1 || l.getY() != 2) return false;
        for (int side = 0; side < 2; side++)
            for (int slot = 0; slot < 4; slot++)
                if (SLOTS_Z[side][slot] == l.getZ()) onPress(new Press(side, slot, now(), p));
        return false;
    }

    private void onPress(Press pr) {
        if (now() < cooldownUntil) return;
        pending[pr.side] = pr;
        Press other = pending[1 - pr.side];
        if (other != null && Math.abs(other.t - pr.t) <= 10) {
            evaluate();
            return;
        }
        later(12, () -> {
            if (pending[pr.side] == pr && pending[1 - pr.side] == null) {
                pending[pr.side] = null;
                cooldownUntil = now() + 20;
                fail();
                flash(false);
                voice().interrupt(voice().pick("r01.desync"));
            }
        });
    }

    private void evaluate() {
        Press n = pending[0], s = pending[1];
        pending[0] = pending[1] = null;
        cooldownUntil = now() + 30;
        if (round > 0) {
            boolean nOk = shown[0][n.slot] == target, sOk = shown[1][s.slot] == target;
            if (!nOk || !sOk) {
                fail();
                flash(false);
                ServerPlayer culprit = !nOk && !sOk ? null : (!nOk ? n.p : s.p);
                voice().interrupt(culprit != null ? voice().pickFor("r01.wrong", culprit) : voice().pick("r01.wrong"));
                return;
            }
        }
        progress();
        flash(true);
        round++;
        if (round >= ROUNDS) {
            phase = 3;
            setObjective("");
            voice().interrupt("r01.done", () -> {
                finish.open(cx, this);
                gateN.open(cx, this);
                gateS.open(cx, this);
                solve();
            });
            return;
        }
        if (round == 1) {
            voice().interrupt("r01.round1.ok");
            voice().say("r01.symbols", () -> {
                setupRound();
                voice().say(screenSide == 0 ? "r01.screen.left" : "r01.screen.right");
                setObjective("Раунд 1/3. У кого горит экран — описывает символ. Нажмите его одновременно.");
            });
        } else {
            voice().interrupt(voice().pick("r01.ok"), () -> {
                setupRound();
                voice().say(screenSide == 0 ? "r01.screen.left" : "r01.screen.right");
                setObjective("Раунд " + round + "/3. Опишите символ с экрана и нажмите его одновременно.");
            });
        }
    }

    @Override
    public String debug() {
        return "phase=" + phase + " round=" + round + " screenSide=" + screenSide;
    }

    @Override
    protected void onReset() {
        phase = 0;
        round = 0;
        splitOnce = false;
        strayTicks = 0;
        target = null;
        pending[0] = pending[1] = null;
        cooldownUntil = 0;
        gateN = null;
        gates();
    }

    @Override
    protected void onSkip() {
        gates();
        finish.setInstant(level(), true);
        gateN.setInstant(level(), true);
        gateS.setInstant(level(), true);
    }
}
