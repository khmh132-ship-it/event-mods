package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.PixelText;
import dev.khmh.trialcomplex.build.Styles;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 2. Архив. Над табло — четыре цвета; каждая цифра кода = число свечей этого цвета в комнате.
 * Свечи спрятаны: под стеклянным полом, в ящиках столов, на люстре, на антресоли, в витрине.
 */
public class R02Candles extends Room {
    static final String CODE = "7492"; // зелёный, красный, синий, жёлтый
    private static final Block[] CAPS = {Blocks.GREEN_CONCRETE, Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.YELLOW_CONCRETE};
    private static final BlockState OFF = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final String[][] KEYS = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"C", "0", "OK"}};
    private static final int[] KEY_Z = {1, 3, 5};

    private String entered = "";
    private boolean locked;

    public R02Candles() {
        super("r02", "Архив", 19, 10, 19);
    }

    static BlockState candle(Block b, int n) {
        return b.defaultBlockState().setValue(CandleBlock.CANDLES, n).setValue(CandleBlock.LIT, true);
    }

    static BlockState fence(boolean n, boolean e, boolean s, boolean w) {
        return Blocks.DARK_OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, n).setValue(FenceBlock.EAST, e)
                .setValue(FenceBlock.SOUTH, s).setValue(FenceBlock.WEST, w);
    }

    @Override
    protected void build(Builder b) {
        b.shell(sx, sy, sz, Styles.ARCH_FLOOR, Styles.ARCH_WALL, Styles.ARCH_CEIL);
        // балки и фонари
        for (int z : new int[]{4, 9, 14}) {
            b.fill(1, sy - 2, z, sx - 2, sy - 2, z, Builder.axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
            for (int x = 3; x < sx - 2; x += 5) if (!(x == 8 && z == 9)) b.set(x, sy - 3, z, Styles.hangingLantern(false));
        }
        // ковры-дорожки
        for (int x = 1; x < sx - 1; x++) b.set(x, 0, 9, Blocks.STRIPPED_SPRUCE_WOOD);

        // табло на северной стене
        b.fill(1, 2, 0, sx - 2, sy - 2, 0, Blocks.POLISHED_BLACKSTONE);
        b.fill(2, 3, 0, 16, 7, 0, OFF);
        for (int i = 0; i < 4; i++) b.fill(2 + 4 * i, 8, 0, 4 + 4 * i, 8, 0, CAPS[i]);
        PixelText.draw(b.level, b.pos(2, 7, 0), Direction.EAST, "____", Blocks.GRAY_CONCRETE.defaultBlockState(), OFF);

        // клавиатура на восточной стене
        b.fill(sx - 1, 1, 1, sx - 1, 6, 6, Blocks.POLISHED_BLACKSTONE_BRICKS);
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 3; col++) {
                int y = 5 - row, z = KEY_Z[col];
                b.set(sx - 1, y, z, Blocks.POLISHED_BLACKSTONE);
                b.set(sx - 2, y, z, Builder.wallButton(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.WEST));
                String k = KEYS[row][col];
                String label = k.equals("C") ? "СБРОС" : k.equals("OK") ? "ВВОД" : k;
                b.sign(sx - 2, y, z + 1, Direction.WEST, k.length() > 1 || k.equals("C") ? DyeColor.ORANGE : DyeColor.WHITE, "", label, "", "");
            }

        // западная стена: стеллажи
        for (int z = 1; z <= 6; z++) b.fill(1, 1, z, 1, 5, z, Blocks.BOOKSHELF);
        for (int z = 12; z <= 17; z++) b.fill(1, 1, z, 1, 5, z, Blocks.BOOKSHELF);
        b.set(1, 6, 3, candle(Blocks.BLUE_CANDLE, 1));
        b.set(1, 6, 6, candle(Blocks.BLUE_CANDLE, 1));
        b.set(1, 6, 5, candle(Blocks.LIGHT_BLUE_CANDLE, 2));
        b.set(1, 6, 14, candle(Blocks.PURPLE_CANDLE, 1));

        // столы у южной стены с ящиками
        for (int x = 2; x <= 10; x++) {
            boolean cabinet = x == 3 || x == 5 || x == 7 || x == 9;
            b.set(x, 1, 17, cabinet ? Blocks.AIR.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState());
            b.set(x, 1, 16, cabinet ? Builder.trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.SOUTH, false, true)
                    : Blocks.DARK_OAK_PLANKS.defaultBlockState());
            b.set(x, 2, 16, Builder.slab(Blocks.SPRUCE_SLAB, true));
            b.set(x, 2, 17, Builder.slab(Blocks.SPRUCE_SLAB, true));
        }
        b.set(3, 1, 17, candle(Blocks.BLUE_CANDLE, 2));
        b.set(5, 1, 17, candle(Blocks.RED_CANDLE, 1));
        b.set(7, 1, 17, candle(Blocks.WHITE_CANDLE, 3));
        b.set(9, 1, 17, candle(Blocks.YELLOW_CANDLE, 1));
        // на столах
        b.set(2, 3, 17, candle(Blocks.ORANGE_CANDLE, 2));
        b.set(4, 3, 17, candle(Blocks.GREEN_CANDLE, 2));
        b.set(5, 3, 16, candle(Blocks.LIME_CANDLE, 2));
        b.set(6, 3, 17, candle(Blocks.BLUE_CANDLE, 1));
        b.set(8, 3, 16, candle(Blocks.RED_CANDLE, 1));
        b.set(10, 3, 16, candle(Blocks.YELLOW_CANDLE, 1));
        b.set(7, 3, 17, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH));
        b.set(9, 3, 17, Blocks.FLOWER_POT);

        // люстра в центре
        b.set(9, 7, 9, Blocks.CHAIN);
        b.set(9, 6, 9, Blocks.CHAIN);
        b.set(9, 5, 9, fence(true, true, true, true));
        b.set(8, 5, 9, fence(false, true, false, false));
        b.set(10, 5, 9, fence(false, false, false, true));
        b.set(9, 5, 8, fence(false, false, true, false));
        b.set(9, 5, 10, fence(true, false, false, false));
        b.set(8, 6, 9, candle(Blocks.RED_CANDLE, 2));
        b.set(10, 6, 9, candle(Blocks.GREEN_CANDLE, 1));
        b.set(9, 6, 8, candle(Blocks.WHITE_CANDLE, 1));
        b.set(9, 6, 10, candle(Blocks.LIGHT_BLUE_CANDLE, 1));

        // витрина
        b.set(13, 1, 8, Blocks.POLISHED_BLACKSTONE);
        b.set(12, 2, 8, Blocks.GLASS);
        b.set(14, 2, 8, Blocks.GLASS);
        b.set(13, 2, 7, Blocks.GLASS);
        b.set(13, 2, 9, Blocks.GLASS);
        b.set(13, 3, 8, Builder.slab(Blocks.POLISHED_BLACKSTONE_SLAB, false));
        for (int dx = -1; dx <= 1; dx += 2)
            for (int dz = -1; dz <= 1; dz += 2) b.set(13 + dx, 2, 8 + dz, Blocks.POLISHED_BLACKSTONE_WALL);
        b.set(13, 2, 8, candle(Blocks.GREEN_CANDLE, 3));

        // окна в полу
        for (int[] w : new int[][]{{6, 12}, {9, 12}, {12, 4}}) {
            b.fill(w[0] - 1, -2, w[1] - 1, w[0] + 1, -1, w[1] + 1, Blocks.DEEPSLATE_TILES);
            b.set(w[0], -1, w[1], Blocks.AIR);
            b.set(w[0], 0, w[1], Blocks.GLASS);
        }
        b.set(6, -1, 12, candle(Blocks.BLUE_CANDLE, 3));
        b.set(9, -1, 12, candle(Blocks.ORANGE_CANDLE, 1));
        b.set(12, -1, 4, Blocks.SOUL_LANTERN);

        // антресоль в юго-восточном углу
        b.fill(12, 4, 12, 17, 4, 17, Blocks.DARK_OAK_PLANKS);
        for (int x = 12; x <= 17; x++) b.set(x, 5, 12, Blocks.BOOKSHELF);
        for (int z = 13; z <= 17; z++) if (z != 13) b.set(12, 5, z, Blocks.BOOKSHELF);
        for (int[] p : new int[][]{{12, 12}, {12, 17}, {17, 12}})
            b.fill(p[0], 1, p[1], p[0], 3, p[1], Builder.axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        b.fill(12, 1, 13, 12, 3, 13, Builder.axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        for (int y = 1; y <= 4; y++)
            b.set(11, y, 13, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        b.set(16, 5, 16, candle(Blocks.GREEN_CANDLE, 1));
        b.set(14, 5, 15, candle(Blocks.BLUE_CANDLE, 1));
        b.set(15, 5, 14, candle(Blocks.PURPLE_CANDLE, 2));
        b.set(17, 5, 17, Blocks.BARREL);
        // уголок под антресолью
        b.set(15, 1, 15, Blocks.BARREL);
        b.set(16, 1, 16, Blocks.CARTOGRAPHY_TABLE);
        b.set(14, 3, 14, Styles.hangingLantern(true));
        for (int x = 13; x <= 17; x++) for (int z = 13; z <= 17; z++) if ((x + z) % 2 == 0) b.set(x, 0, z, Blocks.SPRUCE_PLANKS);
        // мелочи
        b.set(3, 1, 2, Blocks.BARREL);
        b.set(4, 1, 2, Blocks.BARREL);
        b.set(3, 2, 2, Blocks.CHISELED_BOOKSHELF);
        b.set(14, 1, 2, Blocks.CAULDRON);
        b.sign(sx - 2, 5, 8, Direction.WEST, DyeColor.RED, "", "ВЫХОД", "", "");
    }

    private void redraw(BlockState on) {
        String shown = (entered + "____").substring(0, 4);
        PixelText.draw(level(), at(2, 7, 0), Direction.EAST, shown, on, OFF);
    }

    @Override
    protected void onActivate(boolean firstTime) {
        if (firstTime) voice().say("r02.intro");
        setObjective("Найдите код. Цвета над табло — порядок цифр.");
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        BlockPos l = local(pos);
        if (l.getX() != sx - 2 || locked) return false;
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 3; col++)
                if (l.getY() == 5 - row && l.getZ() == KEY_Z[col]) key(p, KEYS[row][col]);
        return false;
    }

    private void key(ServerPlayer p, String k) {
        cx.sound(at(sx - 2, 3, 3), SoundEvents.NOTE_BLOCK_HAT.value(), 0.8f, 1.5f);
        switch (k) {
            case "C" -> entered = "";
            case "OK" -> {
                if (entered.length() < 4) {
                    voice().interrupt("r02.short");
                    return;
                }
                check(p);
                return;
            }
            default -> {
                if (entered.length() < 4) entered += k;
            }
        }
        redraw(Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        progress();
    }

    private void check(ServerPlayer p) {
        if (entered.equals(CODE)) {
            locked = true;
            redraw(Blocks.VERDANT_FROGLIGHT.defaultBlockState());
            cx.sound(at(9, 4, 1), SoundEvents.PLAYER_LEVELUP, 1f, 1f);
            setObjective("");
            voice().interrupt("r02.done", this::solve);
        } else {
            fail();
            locked = true;
            redraw(Blocks.REDSTONE_BLOCK.defaultBlockState());
            cx.sound(at(9, 4, 1), SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.5f);
            voice().interrupt(voice().pickFor("r02.wrong", p));
            later(30, () -> {
                locked = false;
                entered = "";
                redraw(Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            });
        }
    }

    @Override
    protected void onReset() {
        entered = "";
        locked = false;
    }

    @Override
    protected void onSkip() {
        entered = CODE;
        redraw(Blocks.VERDANT_FROGLIGHT.defaultBlockState());
    }
}
