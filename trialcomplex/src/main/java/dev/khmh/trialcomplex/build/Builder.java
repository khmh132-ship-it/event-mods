package dev.khmh.trialcomplex.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Строитель в локальных координатах комнаты. (0,0,0) — угол комнаты, пол на y=0, ходят по y=1.
 * Сид фиксированный — постройка детерминирована, поэтому «сброс комнаты» = постройка заново.
 */
public class Builder {
    public final ServerLevel level;
    public final BlockPos origin;
    public final RandomSource rnd;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public Builder(ServerLevel level, BlockPos origin, long seed) {
        this.level = level;
        this.origin = origin;
        this.rnd = RandomSource.create(seed);
    }

    public BlockPos pos(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    public void set(int x, int y, int z, BlockState s) {
        level.setBlock(pos(x, y, z), s, FLAGS);
    }

    /** С обновлением соседей (стёкла-панели, заборы и т.п. соединятся). */
    public void setU(int x, int y, int z, BlockState s) {
        level.setBlock(pos(x, y, z), s, Block.UPDATE_ALL);
    }

    public void set(int x, int y, int z, Block b) {
        set(x, y, z, b.defaultBlockState());
    }

    public void set(int x, int y, int z, Palette p) {
        set(x, y, z, p.pick(rnd));
    }

    public BlockState get(int x, int y, int z) {
        return level.getBlockState(pos(x, y, z));
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, s);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        fill(x1, y1, z1, x2, y2, z2, b.defaultBlockState());
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Palette p) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, p);
    }

    public void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, Blocks.AIR);
    }

    /** Полый короб: пол, стены, потолок разными палитрами. Внутренность очищается. */
    public void shell(int sx, int sy, int sz, Palette floor, Palette walls, Palette ceil) {
        clear(0, 0, 0, sx - 1, sy - 1, sz - 1);
        fill(0, 0, 0, sx - 1, 0, sz - 1, floor);
        fill(0, sy - 1, 0, sx - 1, sy - 1, sz - 1, ceil);
        fill(0, 1, 0, sx - 1, sy - 2, 0, walls);
        fill(0, 1, sz - 1, sx - 1, sy - 2, sz - 1, walls);
        fill(0, 1, 0, 0, sy - 2, sz - 1, walls);
        fill(sx - 1, 1, 0, sx - 1, sy - 2, sz - 1, walls);
    }

    // ---- состояния-помощники ----

    public static BlockState facing(Block b, Direction d) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
        if (s.hasProperty(BlockStateProperties.FACING)) return s.setValue(BlockStateProperties.FACING, d);
        return s;
    }

    /** Кнопка на стене, «смотрит» в сторону d (т.е. прикреплена к блоку с противоположной стороны). */
    public static BlockState wallButton(Block button, Direction d) {
        return button.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, d);
    }

    public static BlockState floorButton(Block button) {
        return button.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR);
    }

    public static BlockState lever(Direction d, AttachFace face, boolean on) {
        return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, face).setValue(LeverBlock.FACING, d)
                .setValue(LeverBlock.POWERED, on);
    }

    public static BlockState stairs(Block b, Direction d, boolean top) {
        return b.defaultBlockState().setValue(StairBlock.FACING, d).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    public static BlockState slab(Block b, boolean top) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    public static BlockState trapdoor(Block b, Direction d, boolean top, boolean open) {
        return b.defaultBlockState().setValue(TrapDoorBlock.FACING, d)
                .setValue(TrapDoorBlock.HALF, top ? Half.TOP : Half.BOTTOM).setValue(TrapDoorBlock.OPEN, open);
    }

    public static BlockState axis(Block b, Direction.Axis a) {
        return b.defaultBlockState().setValue(BlockStateProperties.AXIS, a);
    }

    /** Настенная табличка (смотрит в сторону d), вощёная, со светящимся текстом. До 4 строк. */
    public void sign(int x, int y, int z, Direction d, DyeColor color, String... lines) {
        sign(Blocks.DARK_OAK_WALL_SIGN, x, y, z, d, color, lines);
    }

    public void sign(Block signBlock, int x, int y, int z, Direction d, DyeColor color, String... lines) {
        BlockPos p = pos(x, y, z);
        level.setBlock(p, signBlock.defaultBlockState().setValue(WallSignBlock.FACING, d), FLAGS);
        if (level.getBlockEntity(p) instanceof SignBlockEntity sb) {
            SignText t = new SignText().setColor(color).setHasGlowingText(true);
            for (int i = 0; i < Math.min(4, lines.length); i++) t = t.setMessage(i, Component.literal(lines[i]));
            sb.setText(t, true);
            sb.setText(t, false);
            sb.setWaxed(true);
            sb.setChanged();
            level.sendBlockUpdated(p, sb.getBlockState(), sb.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
