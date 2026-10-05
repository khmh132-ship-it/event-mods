package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Мелодия: 8 клавиш на полу (до мажор). Голос играет — повторите, наступая на клавиши.
 * 1 — со светом; 2 — только на слух; 3 — со светом, но играть задом наперёд.
 */
public class RMelody extends Room {
    private static final Block[] KEY = {Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE,
            Blocks.CYAN_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.MAGENTA_CONCRETE};
    private static final int[] NOTE = {6, 8, 10, 11, 13, 15, 17, 18}; // до ре ми фа соль ля си до
    private static final int KZ = 3; // клавиши: z=KZ..KZ+1, x=3+2i

    private final int level;
    private final int[] rounds;
    private final RandomSource rnd = RandomSource.create();
    private final List<Integer> seq = new ArrayList<>();
    private final List<Integer> input = new ArrayList<>();
    private int round;
    private boolean accepting, done;

    public RMelody(String id, int level) {
        super(id, "Мелодия " + RLaser.roman(level), 21, 10, 17);
        this.level = level;
        this.rounds = level == 1 ? new int[]{3, 4, 5, 6} : level == 2 ? new int[]{3, 4, 5} : new int[]{4, 5, 6};
    }

    private static float pitch(int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    private int keyX(int i) {
        return 3 + 2 * i;
    }

    private BlockPos replayButton() {
        return at(10, 2, 14);
    }

    @Override
    protected void build(Builder b) {
        Theme.HALL.shell(b, sx, sy, sz);
        // сцена и клавиши
        b.fill(1, 0, 1, sx - 2, 0, KZ + 3, Blocks.POLISHED_BLACKSTONE);
        for (int i = 0; i < 8; i++) {
            b.fill(keyX(i), 0, KZ, keyX(i), 0, KZ + 1, KEY[i]);
            // световые колонны на северной стене
            for (int y = 2; y <= 6; y++) b.set(keyX(i), y, 0, Blocks.BLACK_STAINED_GLASS);
            b.set(keyX(i), 1, 0, KEY[i]);
            b.set(keyX(i), 7, 0, KEY[i]);
            b.set(keyX(i), 1, 1, Blocks.NOTE_BLOCK);
        }
        // кнопка «повторить»
        b.set(10, 1, 14, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(10, 2, 14, Builder.floorButton(Blocks.POLISHED_BLACKSTONE_BUTTON));
        b.sign(10, 1, 13, Direction.NORTH, DyeColor.YELLOW, "", "ПОВТОРИТЬ", "МЕЛОДИЮ", "");
        // зрительские ряды
        for (int x = 2; x < sx - 2; x++) {
            if (x == 10) continue;
            b.set(x, 1, 15, Builder.stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
        }
        for (int x : new int[]{1, sx - 2}) {
            b.set(x, 1, 2, Blocks.JUKEBOX);
            b.set(x, 2, 2, Blocks.NOTE_BLOCK);
        }
    }

    private void column(int i, boolean on) {
        BlockState s = (on ? Blocks.SEA_LANTERN : Blocks.BLACK_STAINED_GLASS).defaultBlockState();
        for (int y = 2; y <= 6; y++) setL(keyX(i), y, 0, s);
    }

    private void play(int i, boolean light) {
        level().playSound(null, at(keyX(i), 1, KZ), SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.RECORDS, 2f, pitch(NOTE[i]));
        if (light) {
            column(i, true);
            later(8, () -> column(i, false));
        }
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        round = 0;
        voice().say(null, this::startRound);
    }

    private void startRound() {
        seq.clear();
        int len = rounds[round];
        int last = -1;
        for (int i = 0; i < len; i++) {
            int k;
            do k = rnd.nextInt(8); while (k == last);
            seq.add(k);
            last = k;
        }
        String what = level == 3 ? " Играйте ЗАДОМ НАПЕРЁД." : "";
        setObjective("Раунд " + (round + 1) + "/" + rounds.length + ": " + len + " нот." + what + " Кнопка сзади — повторить.");
        playback();
    }

    private void playback() {
        accepting = false;
        input.clear();
        boolean light = level != 2;
        for (int i = 0; i < seq.size(); i++) {
            final int k = seq.get(i);
            later(20 + i * 14, () -> play(k, light));
        }
        later(20 + seq.size() * 14 + 6, () -> {
            accepting = true;
            cx.sound(replayButton(), SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 2f);
        });
    }

    private List<Integer> expected() {
        if (level != 3) return seq;
        List<Integer> r = new ArrayList<>(seq);
        java.util.Collections.reverse(r);
        return r;
    }

    @Override
    protected void onStep(ServerPlayer p, BlockPos floor) {
        if (done) return;
        BlockPos l = local(floor);
        if (l.getY() != 0 || l.getZ() < KZ || l.getZ() > KZ + 1) return;
        int i = -1;
        for (int k = 0; k < 8; k++) if (l.getX() == keyX(k)) i = k;
        if (i < 0) return;
        play(i, level != 2);
        if (!accepting) return;
        input.add(i);
        List<Integer> exp = expected();
        int n = input.size();
        if (exp.get(n - 1) != i) {
            accepting = false;
            fail();
            voice().interrupt(voice().pickFor("melody.wrong", p));
            later(50, this::playback);
            return;
        }
        if (n == exp.size()) {
            accepting = false;
            progress();
            round++;
            if (round >= rounds.length) {
                done = true;
                win();
            } else {
                ding(true);
                voice().interrupt(voice().pick("melody.ok"), this::startRound);
            }
        }
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (pos.equals(replayButton()) && accepting && !done) playback();
        return false;
    }

    @Override
    protected void onReset() {
        accepting = done = false;
        round = 0;
    }

    @Override
    public String debug() {
        return "round=" + round + " accepting=" + accepting + " expected=" + expected();
    }
}
