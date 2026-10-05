package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/** Загадки Голоса. Ответ — встать ВДВОЁМ на площадку перед нужной тумбой. */
public class RRiddle extends Room {
    private static final Block[] POOL = {Blocks.LANTERN, Blocks.SPRUCE_LEAVES, Blocks.ANVIL, Blocks.AIR, Blocks.BELL, Blocks.TNT,
            Blocks.BEEHIVE, Blocks.CAULDRON, Blocks.MELON, Blocks.BOOKSHELF, Blocks.CAKE, Blocks.JUKEBOX, Blocks.PUMPKIN,
            Blocks.CHEST, Blocks.COMPOSTER, Blocks.CAMPFIRE, Blocks.FLOWER_POT, Blocks.HAY_BLOCK};
    private static final Block[][] ANSWERS = {
            {Blocks.LANTERN, Blocks.SPRUCE_LEAVES, Blocks.ANVIL, Blocks.AIR, Blocks.BELL},
            {Blocks.TNT, Blocks.BEEHIVE, Blocks.CAULDRON, Blocks.MELON, Blocks.BOOKSHELF},
            {Blocks.CAMPFIRE, Blocks.HAY_BLOCK, Blocks.COMPOSTER, Blocks.PUMPKIN, Blocks.JUKEBOX},
    };
    private static final int PADS = 6;

    private final int level;
    private final RandomSource rnd = RandomSource.create();
    private int q = -1;
    private final Block[] shown = new Block[PADS];
    private boolean asking;
    private int holdPad = -1, holdTicks;

    public RRiddle(String id, int level) {
        super(id, "Загадки " + RLaser.roman(level), 19, 9, 13);
        this.level = level;
    }

    private int padX(int k) {
        return 1 + 3 * k;
    }

    @Override
    protected void build(Builder b) {
        Theme.HALL.shell(b, sx, sy, sz);
        for (int k = 0; k < PADS; k++) {
            int x = padX(k);
            b.fill(x, 0, 4, x + 1, 0, 5, Blocks.GOLD_BLOCK);
            b.fill(x, 1, 2, x + 1, 1, 2, Blocks.QUARTZ_BRICKS);
            b.fill(x, 0, 1, x + 1, 0, 2, Blocks.CHISELED_QUARTZ_BLOCK);
            b.sign(x, 1, 3, Direction.SOUTH, DyeColor.ORANGE, "", String.valueOf(k + 1), "", "");
        }
        // студийные прожекторы
        for (int x = 2; x < sx - 2; x += 4) {
            b.set(x, sy - 2, sz - 2, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        }
        b.sign(sx / 2, 4, sz - 2, Direction.NORTH, DyeColor.YELLOW, "ОТВЕТ —", "ВСТАНЬТЕ ВДВОЁМ", "НА ЗОЛОТО ПЕРЕД", "ТУМБОЙ");
    }

    private void showOptions() {
        Block ans = ANSWERS[level - 1][q];
        List<Block> opts = new ArrayList<>();
        opts.add(ans);
        List<Block> pool = new ArrayList<>(List.of(POOL));
        pool.remove(ans);
        Collections.shuffle(pool, new Random(rnd.nextLong()));
        for (Block bl : pool) { if (opts.size() >= PADS) break; opts.add(bl); }
        Collections.shuffle(opts, new Random(rnd.nextLong()));
        for (int k = 0; k < PADS; k++) {
            shown[k] = opts.get(k);
            BlockState s = opts.get(k).defaultBlockState();
            setL(padX(k), 2, 2, s);
            setL(padX(k) + 1, 2, 2, s);
        }
    }

    private void clearOptions() {
        for (int k = 0; k < PADS; k++) { setL(padX(k), 2, 2, Blocks.AIR.defaultBlockState()); setL(padX(k) + 1, 2, 2, Blocks.AIR.defaultBlockState()); }
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        q = -1;
        voice().say(null, this::nextQuestion);
    }

    private void nextQuestion() {
        q++;
        if (q >= ANSWERS[level - 1].length) {
            clearOptions();
            win();
            return;
        }
        asking = false;
        clearOptions();
        setObjective("Загадка " + (q + 1) + "/" + ANSWERS[level - 1].length + ". Встаньте вдвоём на золото перед ответом.");
        say(id + ".q" + (q + 1), () -> {
            showOptions();
            asking = true;
            holdPad = -1;
        });
    }

    private int padOf(ServerPlayer p) {
        BlockPos l = local(p.blockPosition().below());
        if (l.getY() != 0 || l.getZ() < 4 || l.getZ() > 5) return -1;
        for (int k = 0; k < PADS; k++) if (l.getX() >= padX(k) && l.getX() <= padX(k) + 1) return k;
        return -1;
    }

    @Override
    protected void onTick(long t) {
        if (!asking) return;
        int pad = -2;
        for (ServerPlayer p : players()) {
            if (cx.bypass(p)) continue;
            int k = padOf(p);
            if (pad == -2) pad = k;
            else if (pad != k) pad = -1;
        }
        if (pad < 0) { holdPad = -1; return; }
        if (pad != holdPad) { holdPad = pad; holdTicks = 0; return; }
        if (++holdTicks < 30) return;
        asking = false;
        Block ans = ANSWERS[level - 1][q];
        if (shown[pad] == ans) {
            ding(true);
            progress();
            voice().interrupt(id + ".a" + (q + 1), this::nextQuestion);
        } else {
            fail();
            ding(false);
            voice().interrupt(voice().pick("riddle.wrong"));
            voice().say(null, () -> { showOptions(); asking = true; holdPad = -1; });
        }
    }

    @Override
    protected void onReset() {
        asking = false;
        q = -1;
    }

    @Override
    public String debug() {
        if (q < 0 || q >= ANSWERS[level - 1].length) return "q=" + q;
        Block ans = ANSWERS[level - 1][q];
        int idx = -1;
        for (int k = 0; k < PADS; k++) if (shown[k] == ans) idx = k;
        return "q=" + q + " asking=" + asking + " answerPad=" + idx;
    }
}
