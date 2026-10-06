package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Palette;
import dev.khmh.trialcomplex.build.PixelText;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Gate;
import dev.khmh.trialcomplex.game.Room;
import dev.khmh.trialcomplex.parts.Keypad;
import dev.khmh.trialcomplex.parts.Split;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * «Быки и коровы»: угадать 4 разные цифры. Бык — цифра на своём месте, корова — есть, но не там.
 * Уровень 2 — зал разделён: у одного клавиатура и табло, у другого — журнал ответов.
 */
public class RCows extends Room {
    private static final BlockState OFF = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final int DIV_Z = 8, FIN_X = 26;

    private final int level;
    private final Keypad keypad;
    private final int digits;
    private final RandomSource rnd = RandomSource.create();
    private String secret = "", entered = "";
    private final List<String> history = new ArrayList<>();
    private boolean locked;
    private Split split;
    private Gate finN, finS;

    public RCows(String id, int level) {
        super(id, "Быки и коровы " + RLaser.roman(level), 31, 10, 17);
        this.level = level;
        this.digits = level == 3 ? 5 : 4;
        this.keypad = new Keypad(new BlockPos(level == 3 ? 23 : 19, 5, 1), Direction.SOUTH);
    }

    private int maxTries() {
        return level == 1 ? 10 : level == 2 ? 12 : 14;
    }

    @Override
    protected void init() {
        if (level == 2) {
            Palette w = Palette.of(Blocks.POLISHED_BLACKSTONE_BRICKS);
            split = new Split(new AABB(v(5, 1, 1), v(FIN_X, sy - 1, DIV_Z)), new AABB(v(5, 1, DIV_Z + 1), v(FIN_X, sy - 1, sz - 1)),
                    new Gate(at(4, 1, 1), at(4, sy - 2, DIV_Z - 1), w), new Gate(at(4, 1, DIV_Z + 1), at(4, sy - 2, sz - 2), w));
            finN = new Gate(at(FIN_X, 1, DIV_Z - 2), at(FIN_X, 4, DIV_Z - 1), Palette.of(Blocks.IRON_BLOCK));
            finS = new Gate(at(FIN_X, 1, DIV_Z + 1), at(FIN_X, 4, DIV_Z + 2), Palette.of(Blocks.IRON_BLOCK));
        }
    }

    @Override
    protected void build(Builder b) {
        Theme.VAULT.shell(b, sx, sy, sz);
        // табло
        int w = digits * 4 - 1;
        b.fill(1, 2, 0, w + 2, 8, 0, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.fill(2, 3, 0, w + 1, 7, 0, OFF);
        PixelText.draw(b.level, b.pos(2, 7, 0), Direction.EAST, "_".repeat(digits), Blocks.GRAY_CONCRETE.defaultBlockState(), OFF);
        keypad.build(b);
        // журнал на южной стене
        for (int i = 0; i < maxTries(); i++) {
            BlockPos s = slot(i);
            b.sign(s.getX(), s.getY(), s.getZ(), Direction.NORTH, DyeColor.WHITE, "", "попытка " + (i + 1), "—", "");
        }
        b.sign(2, 4, sz - 2, Direction.NORTH, DyeColor.YELLOW, "БЫК — ЦИФРА", "НА СВОЁМ МЕСТЕ", "КОРОВА — ЕСТЬ,", "НО НЕ ТАМ");
        b.sign(2, 3, sz - 2, Direction.NORTH, DyeColor.YELLOW, "ВСЕ " + digits + " ЦИФР" + (digits == 4 ? "Ы" : ""), "РАЗНЫЕ", "", "");
        // сейфовые декорации
        for (int x : new int[]{6, 12, 18}) {
            b.set(x, 1, sz - 2, Blocks.GOLD_BLOCK);
            b.set(x, 2, sz - 2, Builder.facing(Blocks.CHEST, Direction.NORTH));
        }
        b.set(10, 1, 7, Blocks.POLISHED_BLACKSTONE);
        b.set(10, 2, 7, Builder.slab(Blocks.POLISHED_BLACKSTONE_SLAB, false));
        if (level == 2) {
            b.fill(4, 1, DIV_Z, FIN_X, sy - 2, DIV_Z, Palette.of(Blocks.POLISHED_BLACKSTONE_BRICKS, 3, Blocks.GILDED_BLACKSTONE, 1));
            b.fill(FIN_X, 1, 1, FIN_X, sy - 2, sz - 2, Blocks.POLISHED_BLACKSTONE);
            b.fill(FIN_X + 1, 1, 1, sx - 2, sy - 2, DIV_Z - 3, Blocks.POLISHED_BLACKSTONE);
            b.fill(FIN_X + 1, 1, DIV_Z + 3, sx - 2, sy - 2, sz - 2, Blocks.POLISHED_BLACKSTONE);
            b.fill(FIN_X + 1, 5, DIV_Z - 2, sx - 2, sy - 2, DIV_Z + 2, Blocks.POLISHED_BLACKSTONE);
            split.reset();
            for (Gate g : List.of(finN, finS)) g.setInstant(b.level, false);
            b.sign(3, 3, DIV_Z - 1, Direction.WEST, DyeColor.WHITE, "", "КЛАВИАТУРА", "", "");
            b.sign(3, 3, DIV_Z + 1, Direction.WEST, DyeColor.WHITE, "", "ЖУРНАЛ", "", "");
        }
    }

    private BlockPos slot(int i) {
        int col = i % 6, row = i / 6;
        return new BlockPos(5 + col * 3, 3 - row, sz - 2);
    }

    private void newSecret() {
        StringBuilder s = new StringBuilder();
        while (s.length() < digits) {
            char c = (char) ('0' + rnd.nextInt(10));
            if (s.indexOf(String.valueOf(c)) < 0) s.append(c);
        }
        secret = s.toString();
        history.clear();
        entered = "";
        redraw(Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        for (int i = 0; i < maxTries(); i++) writeSlot(i, "", "попытка " + (i + 1), "—");
    }

    private void writeSlot(int i, String a, String b2, String c) {
        BlockPos s = slot(i);
        new Builder(level(), origin, 1).sign(s.getX(), s.getY(), s.getZ(), Direction.NORTH, DyeColor.WHITE, a, b2, c, "");
    }

    private void redraw(BlockState on) {
        PixelText.draw(level(), at(2, 7, 0), Direction.EAST, (entered + "_____").substring(0, digits), on, OFF);
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        newSecret();
        setObjective(level != 2 ? "Угадайте код из " + digits + " разных цифр. Попыток: " + maxTries() + "."
                : "Один — у клавиатуры, другой — у журнала. Разойдитесь. Попыток: " + maxTries() + ".");
    }

    @Override
    protected void onTick(long t) {
        if (split != null && split.tick(cx, this)) say(id + ".split");
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (locked) return false;
        String k = keypad.keyAt(local(pos));
        if (k == null) return false;
        cx.sound(pos, SoundEvents.NOTE_BLOCK_HAT.value(), 0.8f, 1.5f);
        if (k.equals("C")) entered = "";
        else if (k.equals("OK")) {
            if (entered.length() < digits) { sayNow(digits == 4 ? "cows.short" : "cows.short5", null); return false; }
            check(p);
            return false;
        } else if (entered.length() < digits && !entered.contains(k)) entered += k;
        else if (entered.contains(k)) sayNow("cows.repeat", null);
        redraw(Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        progress();
        return false;
    }

    private void check(ServerPlayer p) {
        int bulls = 0, cows = 0;
        for (int i = 0; i < digits; i++) {
            char c = entered.charAt(i);
            if (secret.charAt(i) == c) bulls++;
            else if (secret.indexOf(c) >= 0) cows++;
        }
        int n = history.size();
        history.add(entered);
        writeSlot(n, "#" + (n + 1) + ":  " + String.join(" ", entered.split("")), "Быки: " + bulls, "Коровы: " + cows);
        cx.sound(at(10, 3, sz - 2), SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.6f + bulls * 0.25f);
        progress();
        if (bulls == digits) {
            locked = true;
            redraw(Blocks.VERDANT_FROGLIGHT.defaultBlockState());
            if (finN != null) { finN.open(cx, this); finS.open(cx, this); split.open(cx, this); }
            win();
            return;
        }
        fail();
        locked = true;
        redraw(Blocks.REDSTONE_BLOCK.defaultBlockState());
        if (history.size() >= maxTries()) {
            voice().interrupt("cows.out", () -> {
                newSecret();
                locked = false;
            });
            return;
        }
        if (bulls + cows == 0) sayNow("cows.zero", p);
        else if (history.size() % 3 == 0) sayNow("cows.taunt", p);
        later(25, () -> {
            locked = false;
            entered = "";
            redraw(Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        });
    }

    @Override
    protected void onReset() {
        locked = false;
        entered = "";
        history.clear();
        if (split != null) split.reset();
    }

    @Override
    protected void onSkip() {
        if (finN != null) { finN.setInstant(level(), true); finS.setInstant(level(), true); split.open(cx, this); }
    }

    @Override
    public String debug() {
        return "secret=" + secret + " tries=" + history.size();
    }
}
