package dev.khmh.trialcomplex.parts;

import dev.khmh.trialcomplex.build.Builder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Настенная клавиатура 3×4: кнопки с подписями-табличками справа от каждой.
 * Координаты локальные (в системе комнаты). facing — куда «смотрят» кнопки (из стены в комнату).
 */
public final class Keypad {
    public static final String[][] KEYS = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"C", "0", "OK"}};
    private final BlockPos topLeft;   // кнопка «1» (локально)
    private final Direction facing, right;
    private final Block button;

    public Keypad(BlockPos topLeftButton, Direction facing, Block button) {
        this.topLeft = topLeftButton;
        this.facing = facing;
        this.right = facing.getCounterClockWise(); // смотрим на стену — правая рука
        this.button = button;
    }

    public Keypad(BlockPos topLeftButton, Direction facing) {
        this(topLeftButton, facing, Blocks.POLISHED_BLACKSTONE_BUTTON);
    }

    public BlockPos buttonPos(int row, int col) {
        return topLeft.relative(right, col * 2).below(row);
    }

    public void build(Builder b) {
        Direction back = facing.getOpposite();
        // подложка
        for (int row = -1; row <= 4; row++)
            for (int col = -1; col <= 6; col++) {
                BlockPos w = topLeft.relative(right, col).below(row).relative(back);
                b.set(w.getX(), w.getY(), w.getZ(), (row == -1 || row == 4 || col == -1 || col == 6)
                        ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            }
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 3; col++) {
                BlockPos p = buttonPos(row, col);
                b.set(p.getX(), p.getY(), p.getZ(), Builder.wallButton(button, facing));
                BlockPos s = p.relative(right);
                String k = KEYS[row][col];
                String label = k.equals("C") ? "СБРОС" : k.equals("OK") ? "ВВОД" : k;
                b.sign(s.getX(), s.getY(), s.getZ(), facing, k.length() > 1 || k.equals("C") ? DyeColor.ORANGE : DyeColor.WHITE, "", label, "", "");
            }
    }

    /** Какая клавиша нажата (локальная позиция кнопки) или null. */
    public String keyAt(BlockPos local) {
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 3; col++)
                if (buttonPos(row, col).equals(local)) return KEYS[row][col];
        return null;
    }
}
