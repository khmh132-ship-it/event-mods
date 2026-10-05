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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Шифр: буквы заменены узорами глазурованной керамики. На стене — слова-подсказки с символами,
 * выше — зашифрованное сообщение (четыре числа словами). Код — эти числа.
 * Уровень 2: зал разделён — ключ у одного, сообщение у другого.
 */
public class RCipher extends Room {
    private static final Block[] GLYPH = {Blocks.WHITE_GLAZED_TERRACOTTA, Blocks.ORANGE_GLAZED_TERRACOTTA, Blocks.MAGENTA_GLAZED_TERRACOTTA,
            Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, Blocks.YELLOW_GLAZED_TERRACOTTA, Blocks.LIME_GLAZED_TERRACOTTA, Blocks.PINK_GLAZED_TERRACOTTA,
            Blocks.CYAN_GLAZED_TERRACOTTA, Blocks.PURPLE_GLAZED_TERRACOTTA, Blocks.BLUE_GLAZED_TERRACOTTA, Blocks.BROWN_GLAZED_TERRACOTTA,
            Blocks.GREEN_GLAZED_TERRACOTTA, Blocks.RED_GLAZED_TERRACOTTA, Blocks.BLACK_GLAZED_TERRACOTTA, Blocks.GRAY_GLAZED_TERRACOTTA,
            Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA};
    private static final String[] MESSAGE = {"ТРИ СЕМЬ ДВА ОДИН", "ПЯТЬ НОЛЬ ВОСЕМЬ ДВА"};
    private static final String[] CODE = {"3721", "5082"};
    private static final String[][] KEY_WORDS = {{"РИС", "ТЕНЬ", "ВОДА", "ДОМ"}, {"ПЕНА", "ЛЯМКА", "ВОЛОС", "ТЕНЬ", "ДЫМ"}};
    private static final BlockState OFF = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final int DIV_Z = 9, FIN_X = 25;

    private final int level;
    private final Map<Character, Block> map = new HashMap<>();
    private final Keypad keypad;
    private String entered = "";
    private boolean locked;
    private Split split;
    private Gate finN, finS;

    public RCipher(String id, int level) {
        super(id, "Шифр " + RLaser.roman(level), 30, 11, 19);
        this.level = level;
        this.keypad = new Keypad(new BlockPos(FIN_X - 1, 5, 2), Direction.WEST);
        RandomSource r = RandomSource.create(id.hashCode() * 3L);
        List<Block> glyphs = new ArrayList<>(List.of(GLYPH));
        Collections.shuffle(glyphs, new Random(r.nextLong()));
        int i = 0;
        Set<Character> letters = new TreeSet<>();
        for (char c : MESSAGE[level - 1].toCharArray()) if (c != ' ') letters.add(c);
        for (String w : KEY_WORDS[level - 1]) for (char c : w.toCharArray()) letters.add(c);
        for (char c : letters) map.put(c, glyphs.get(i++ % glyphs.size()));
    }

    @Override
    protected void init() {
        if (level == 2) {
            Palette w = Palette.of(Blocks.STONE_BRICKS);
            split = new Split(new AABB(v(5, 1, 1), v(FIN_X, sy - 1, DIV_Z)), new AABB(v(5, 1, DIV_Z + 1), v(FIN_X, sy - 1, sz - 1)),
                    new Gate(at(4, 1, 1), at(4, sy - 2, DIV_Z - 1), w), new Gate(at(4, 1, DIV_Z + 1), at(4, sy - 2, sz - 2), w));
            finN = new Gate(at(FIN_X, 1, DIV_Z - 2), at(FIN_X, 4, DIV_Z - 1), Palette.of(Blocks.IRON_BLOCK));
            finS = new Gate(at(FIN_X, 1, DIV_Z + 1), at(FIN_X, 4, DIV_Z + 2), Palette.of(Blocks.IRON_BLOCK));
        }
    }

    /** Символ в стене: глазурь повёрнута так, чтобы с обеих стен (север/юг) узор выглядел одинаково. */
    private BlockState glyph(char c, boolean southWall) {
        return map.get(c).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, southWall ? Direction.SOUTH : Direction.NORTH);
    }

    @Override
    protected void build(Builder b) {
        Theme.LOGIC.shell(b, sx, sy, sz);
        boolean split2 = level == 2;
        // сообщение: северная стена, высоко
        String msg = MESSAGE[level - 1];
        int mx = split2 ? 5 : 3;
        b.fill(mx - 1, 6, 0, mx + msg.length(), 8, 0, Blocks.POLISHED_BLACKSTONE_BRICKS);
        for (int i = 0; i < msg.length(); i++) {
            char c = msg.charAt(i);
            b.set(mx + i, 7, 0, c == ' ' ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : glyph(c, false));
        }
        b.sign(mx + msg.length() / 2, 5, 1, Direction.SOUTH, DyeColor.YELLOW, "СООБЩЕНИЕ:", "ЧЕТЫРЕ ЧИСЛА", "СЛОВАМИ", "= КОД");
        // ключ: слова с символами (ур.1 — северная стена ниже; ур.2 — южная стена, в южной половине)
        int kz = split2 ? sz - 1 : 0;
        int signZ = split2 ? sz - 2 : 1;
        Direction face = split2 ? Direction.NORTH : Direction.SOUTH;
        int xs = split2 ? 6 : 2, xe = FIN_X - 3;
        int x = xs, y = 2;
        for (String w : KEY_WORDS[level - 1]) {
            if (x + w.length() - 1 > xe) { x = xs; y += 3; }
            for (int i = 0; i < w.length(); i++) {
                int gx = split2 ? xs + xe - (x + i) : x + i; // на южной стене читаем справа налево по x
                b.set(gx, y, kz, glyph(w.charAt(i), split2));
            }
            int mid = split2 ? xs + xe - (x + w.length() / 2) : x + w.length() / 2;
            b.sign(mid, y + 1, signZ, face, DyeColor.WHITE, "", w, "", "");
            x += w.length() + 2;
        }
        keypad.build(b);
        b.set(FIN_X, 7, 4, Blocks.POLISHED_BLACKSTONE_BRICKS);
        showEntered(b);
        if (split2) {
            b.fill(4, 1, DIV_Z, FIN_X, sy - 2, DIV_Z, Palette.of(Blocks.STONE_BRICKS, 3, Blocks.MOSSY_STONE_BRICKS, 1));
            b.fill(FIN_X, 1, 1, FIN_X, sy - 2, sz - 2, Blocks.STONE_BRICKS);
            b.fill(FIN_X + 1, 1, 1, sx - 2, sy - 2, DIV_Z - 3, Blocks.STONE_BRICKS);
            b.fill(FIN_X + 1, 1, DIV_Z + 3, sx - 2, sy - 2, sz - 2, Blocks.STONE_BRICKS);
            b.fill(FIN_X + 1, 5, DIV_Z - 2, sx - 2, sy - 2, DIV_Z + 2, Blocks.STONE_BRICKS);
            split.reset();
            finN.setInstant(b.level, false);
            finS.setInstant(b.level, false);
            b.sign(3, 3, DIV_Z - 1, Direction.WEST, DyeColor.WHITE, "", "СООБЩЕНИЕ", "", "");
            b.sign(3, 3, DIV_Z + 1, Direction.WEST, DyeColor.WHITE, "", "КЛЮЧ", "", "");
        }
    }

    private void showEntered(Builder b) {
        String shown = String.join(" ", (entered + "____").substring(0, 4).split(""));
        b.sign(FIN_X - 1, 7, 4, Direction.WEST, DyeColor.YELLOW, "КОД:", shown, "", "");
    }

    private void redraw(BlockState on) {
        showEntered(new Builder(level(), origin, 1));
        if (on.is(Blocks.VERDANT_FROGLIGHT) || on.is(Blocks.REDSTONE_BLOCK)) set(at(FIN_X, 8, 4), on);
        else set(at(FIN_X, 8, 4), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        setObjective(level == 1 ? "Расшифруйте сообщение по словам-подсказкам и введите код."
                : "Один — у сообщения, другой — у ключа. Расшифруйте вместе и введите код.");
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
            if (entered.equals(CODE[level - 1])) {
                locked = true;
                redraw(Blocks.VERDANT_FROGLIGHT.defaultBlockState());
                if (finN != null) { finN.open(cx, this); finS.open(cx, this); split.open(cx, this); }
                win();
            } else {
                fail();
                locked = true;
                redraw(Blocks.REDSTONE_BLOCK.defaultBlockState());
                sayNow("cipher.wrong", p);
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
        if (split != null) split.reset();
    }

    @Override
    protected void onSkip() {
        if (finN != null) { finN.setInstant(level(), true); finS.setInstant(level(), true); split.open(cx, this); }
    }

    @Override
    public String debug() {
        return "code=" + CODE[level - 1];
    }
}
