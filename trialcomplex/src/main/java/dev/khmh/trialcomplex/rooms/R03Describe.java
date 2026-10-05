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

import java.util.List;

/**
 * 3. Описатель. Два зала без окон. У одного — картинка на стене, у другого — сетка плиток.
 * Первый описывает, второй собирает. Три раунда: 3×3, 4×4, 5×5 (последний — зеркальный экран).
 */
public class R03Describe extends Room {
    private static final int[] SIZES = {3, 4, 5};
    private static final Block[][] COLORS = {
            {Blocks.WHITE_CONCRETE, Blocks.BLACK_CONCRETE},
            {Blocks.WHITE_CONCRETE, Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE},
            {Blocks.WHITE_CONCRETE, Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.YELLOW_CONCRETE}};
    private static final BlockState INACTIVE = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState SCREEN_OFF = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final int WALL_E = 20;

    private final RandomSource rnd = RandomSource.create();
    private Gate gateN, gateS, finN, finS;
    private int phase; // 0 вступление, 1 ждём расхода, 2 раунды, 3 готово
    private int round, displaySide;
    private int[][] pattern = new int[5][5];
    private final int[][] input = new int[5][5];
    private boolean busy, splitOnce;
    private int strayTicks;
    private String savedObjective = "";

    public R03Describe() {
        super("r03", "Описатель", 24, 9, 19);
    }

    // ---------- координаты сеток ----------

    /** Пиксель экрана стороны side: строка r сверху, колонка c слева (смотрим на восток). */
    private BlockPos screen(int side, int r, int c) {
        int zLeft = side == 0 ? 2 : 12;
        return at(WALL_E, 6 - r, zLeft + c);
    }

    /** Плитка сетки ввода стороны side (север — стена z=0, смотрим на север; юг — стена z=sz-1, смотрим на юг). */
    private BlockPos cell(int side, int r, int c) {
        return side == 0 ? at(9 + c, 6 - r, 0) : at(13 - c, 6 - r, sz - 1);
    }

    private BlockPos checkButton(int side) {
        return side == 0 ? at(15, 3, 1) : at(7, 3, sz - 2);
    }

    @Override
    protected void build(Builder b) {
        Styles.lab(b, sx, sy, sz, Blocks.SEA_LANTERN.defaultBlockState(), Blocks.LIME_TERRACOTTA);
        Palette div = Palette.of(Blocks.DEEPSLATE_TILES, 4, Blocks.POLISHED_DEEPSLATE, 1);
        b.fill(4, 1, 9, WALL_E, sy - 2, 9, div);
        b.fill(WALL_E, 1, 1, WALL_E, sy - 2, sz - 2, Styles.LAB_WALL);
        // зона выхода
        b.fill(WALL_E + 1, 1, 1, sx - 2, sy - 2, 6, Styles.LAB_WALL);
        b.fill(WALL_E + 1, 1, 12, sx - 2, sy - 2, sz - 2, Styles.LAB_WALL);
        b.fill(WALL_E + 1, 5, 7, sx - 2, sy - 2, 11, Styles.LAB_WALL);
        b.fill(WALL_E, 1, 9, WALL_E + 1, 4, 9, Blocks.POLISHED_DEEPSLATE);
        for (int side = 0; side < 2; side++) {
            // рамки экранов и сеток
            int zLeft = side == 0 ? 2 : 12;
            b.fill(WALL_E, 1, zLeft - 1, WALL_E, 7, zLeft + 5, Blocks.POLISHED_BLACKSTONE_BRICKS);
            for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) b.level.setBlock(screen(side, r, c), SCREEN_OFF, 2);
            int wz = side == 0 ? 0 : sz - 1;
            b.fill(8, 1, wz, 14, 7, wz, Blocks.POLISHED_BLACKSTONE_BRICKS);
            for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) b.level.setBlock(cell(side, r, c), INACTIVE, 2);
            // кнопка проверки
            BlockPos btn = checkButton(side);
            BlockPos l = local(btn);
            b.set(l.getX(), l.getY(), wz, Blocks.EMERALD_BLOCK);
            b.set(l.getX(), l.getY(), l.getZ(), Builder.wallButton(Blocks.POLISHED_BLACKSTONE_BUTTON, side == 0 ? Direction.SOUTH : Direction.NORTH));
            b.sign(l.getX(), l.getY() + 1, l.getZ(), side == 0 ? Direction.SOUTH : Direction.NORTH, DyeColor.LIME, "", "ПРОВЕРКА", "", "");
        }
        b.sign(3, 3, 8, Direction.WEST, DyeColor.WHITE, "", "ЛЕВЫЙ", "ЗАЛ", "");
        b.sign(3, 3, 10, Direction.WEST, DyeColor.WHITE, "", "ПРАВЫЙ", "ЗАЛ", "");
        gates().forEach(g -> g.setInstant(b.level, g == gateN || g == gateS));
    }

    @Override
    protected void init() {
        gates();
    }

    private List<Gate> gates() {
        if (gateN == null) {
            Palette wall = Palette.of(Blocks.WHITE_CONCRETE, 3, Blocks.LIGHT_GRAY_CONCRETE, 1);
            gateN = new Gate(at(4, 1, 1), at(4, sy - 2, 8), wall);
            gateS = new Gate(at(4, 1, 10), at(4, sy - 2, sz - 2), wall);
            finN = new Gate(at(WALL_E, 1, 7), at(WALL_E, 4, 8), Palette.of(Blocks.IRON_BLOCK));
            finS = new Gate(at(WALL_E, 1, 10), at(WALL_E, 4, 11), Palette.of(Blocks.IRON_BLOCK));
        }
        return List.of(gateN, gateS, finN, finS);
    }

    private AABB chamber(int side) {
        return side == 0 ? new AABB(v(5, 1, 1), v(WALL_E, sy - 1, 9)) : new AABB(v(5, 1, 10), v(WALL_E, sy - 1, sz - 1));
    }

    private ServerPlayer playerIn(int side) {
        for (ServerPlayer p : cx.players()) if (chamber(side).contains(p.position())) return p;
        return null;
    }

    @Override
    protected void onActivate(boolean firstTime) {
        phase = 1;
        if (firstTime) voice().say("r03.intro");
        setObjective("Разойдитесь по залам: один налево, другой направо.");
    }

    @Override
    protected void onTick(long t) {
        if (phase == 2 && cx.players().size() >= 2 && (playerIn(0) == null || playerIn(1) == null)) {
            // кто-то вылетел/вернулся в лобби — открываем залы, раунд сохраняется
            if (++strayTicks > 40) {
                strayTicks = 0;
                phase = 1;
                savedObjective = objective();
                gateN.open(cx, this);
                gateS.open(cx, this);
                setObjective("Разойдитесь по залам снова — продолжим с того же места.");
            }
            return;
        }
        strayTicks = 0;
        if (phase != 1 || voice().busy()) return;
        ServerPlayer a = playerIn(0), b = playerIn(1);
        if (a == null || b == null || a == b) return;
        if (a.getBoundingBox().intersects(gateN.box()) || b.getBoundingBox().intersects(gateS.box())) return;
        gateN.close(cx, this);
        gateS.close(cx, this);
        phase = 2;
        progress();
        if (splitOnce) {
            setObjective(savedObjective);
            return;
        }
        splitOnce = true;
        round = 0;
        displaySide = rnd.nextInt(2);
        voice().say("r03.split", this::startRound);
    }

    private void startRound() {
        int n = SIZES[round];
        int k = COLORS[round].length;
        // случайный узор, где каждый цвет встречается хотя бы раз и «фон» не доминирует полностью
        do {
            for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) pattern[r][c] = rnd.nextInt(k);
        } while (!allColorsUsed(n, k));
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) input[r][c] = 0;
        int inSide = 1 - displaySide;
        for (int side = 0; side < 2; side++)
            for (int r = 0; r < 5; r++)
                for (int c = 0; c < 5; c++) {
                    boolean live = r < n && c < n;
                    // экран
                    BlockState s = SCREEN_OFF;
                    if (side == displaySide && live) {
                        int pc = round == 2 ? n - 1 - c : c; // последний раунд — зеркально
                        s = COLORS[round][pattern[r][pc]].defaultBlockState();
                    }
                    level().setBlock(screen(side, r, c), s, Block.UPDATE_CLIENTS);
                    // сетка
                    BlockState g = side == inSide && live ? COLORS[round][0].defaultBlockState() : INACTIVE;
                    level().setBlock(cell(side, r, c), g, Block.UPDATE_CLIENTS);
                }
        cx.sound(at(WALL_E, 4, 9), SoundEvents.BEACON_POWER_SELECT, 1f, 1.2f);
        voice().say(displaySide == 0 ? "r03.screen.left" : "r03.screen.right");
        setObjective("Раунд " + (round + 1) + "/3: " + n + "×" + n + ". У " + (displaySide == 0 ? "левого" : "правого")
                + " — картинка, у " + (displaySide == 0 ? "правого" : "левого") + " — сетка. ПКМ по плиткам, потом «ПРОВЕРКА».");
    }

    private boolean allColorsUsed(int n, int k) {
        boolean[] used = new boolean[k];
        int bg = 0;
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                used[pattern[r][c]] = true;
                if (pattern[r][c] == 0) bg++;
            }
        for (boolean u : used) if (!u) return false;
        return bg >= n && bg <= n * n - n;
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (phase != 2 || busy) return false;
        int inSide = 1 - displaySide;
        int n = SIZES[round];
        if (pos.equals(checkButton(inSide))) {
            check(p);
            return false;
        }
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                if (pos.equals(cell(inSide, r, c))) {
                    input[r][c] = (input[r][c] + 1) % COLORS[round].length;
                    level().setBlock(pos, COLORS[round][input[r][c]].defaultBlockState(), Block.UPDATE_CLIENTS);
                    cx.sound(pos, SoundEvents.STONE_BUTTON_CLICK_ON, 0.6f, 1.4f + 0.1f * input[r][c]);
                    progress();
                    return true;
                }
        return false;
    }

    private void check(ServerPlayer p) {
        int n = SIZES[round];
        int inSide = 1 - displaySide;
        int wrong = 0;
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) if (input[r][c] != pattern[r][c]) wrong++;
        if (wrong > 0) {
            fail();
            cx.sound(checkButton(inSide), SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.5f);
            voice().interrupt(voice().pickFor("r03.wrong", p));
            if (round < 2) {
                busy = true;
                voice().say("r03.tnt");
                for (int r = 0; r < n; r++)
                    for (int c = 0; c < n; c++)
                        if (input[r][c] != pattern[r][c]) level().setBlock(cell(inSide, r, c), Blocks.TNT.defaultBlockState(), Block.UPDATE_CLIENTS);
                later(40, () -> {
                    busy = false;
                    for (int r = 0; r < n; r++)
                        for (int c = 0; c < n; c++)
                            level().setBlock(cell(inSide, r, c), COLORS[round][input[r][c]].defaultBlockState(), Block.UPDATE_CLIENTS);
                });
            }
            return;
        }
        progress();
        cx.sound(checkButton(inSide), SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
        round++;
        busy = true;
        if (round >= SIZES.length) {
            phase = 3;
            setObjective("");
            voice().interrupt("r03.done", () -> {
                for (Gate g : gates()) g.open(cx, this);
                solve();
            });
            return;
        }
        displaySide = 1 - displaySide;
        voice().interrupt(voice().pick("r03.ok"));
        voice().say("r03.round" + (round + 1), () -> {
            busy = false;
            startRound();
        });
    }

    @Override
    public String debug() {
        return "phase=" + phase + " round=" + round + " displaySide=" + displaySide + " busy=" + busy;
    }

    @Override
    protected void onReset() {
        phase = 0;
        round = 0;
        busy = false;
        splitOnce = false;
        strayTicks = 0;
        gateN = null;
        gates();
    }

    @Override
    protected void onSkip() {
        for (Gate g : gates()) g.setInstant(level(), true);
    }
}
