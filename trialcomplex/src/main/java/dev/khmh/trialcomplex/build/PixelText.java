package dev.khmh.trialcomplex.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Табло из блоков: шрифт 3×5. Колонки идут в сторону right, строки — сверху вниз. */
public final class PixelText {
    private static final String[] FONT = {
            "111101101101111", // 0
            "010110010010111", // 1
            "111001111100111", // 2
            "111001111001111", // 3
            "101101111001001", // 4
            "111100111001111", // 5
            "111100111101111", // 6
            "111001001010010", // 7
            "111101111101111", // 8
            "111101111001111", // 9
    };
    private static final String UNDERSCORE = "000000000000111";
    private static final String DASH = "000000111000000";
    private static final String BLANK = "000000000000000";

    private PixelText() {}

    private static String glyph(char c) {
        if (c >= '0' && c <= '9') return FONT[c - '0'];
        if (c == '_') return UNDERSCORE;
        if (c == '-') return DASH;
        return BLANK;
    }

    /**
     * Нарисовать строку. topLeft — верхний левый пиксель первого символа (мировые координаты).
     * Между символами — 1 пиксель фона.
     */
    public static void draw(ServerLevel lvl, BlockPos topLeft, Direction right, String text, BlockState on, BlockState off) {
        for (int i = 0; i < text.length(); i++) {
            String g = glyph(text.charAt(i));
            for (int row = 0; row < 5; row++)
                for (int col = 0; col < 3; col++) {
                    BlockPos p = topLeft.relative(right, i * 4 + col).below(row);
                    lvl.setBlock(p, g.charAt(row * 3 + col) == '1' ? on : off, Block.UPDATE_CLIENTS);
                }
            if (i + 1 < text.length())
                for (int row = 0; row < 5; row++)
                    lvl.setBlock(topLeft.relative(right, i * 4 + 3).below(row), off, Block.UPDATE_CLIENTS);
        }
    }
}
