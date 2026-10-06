package dev.khmh.trialcomplex.game;

import dev.khmh.trialcomplex.build.Palette;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Гермодверь: прямоугольник блоков, который мод убирает/ставит с анимацией по рядам. */
public final class Gate {
    private final BlockPos min, max;
    private final Palette closed;
    private long busyUntil;
    private BlockPos indicator;

    /** Лампа-индикатор над дверью: зелёная — открыто, красная — закрыто. */
    public Gate withIndicator(BlockPos p) {
        this.indicator = p;
        return this;
    }

    private void indicate(ServerLevel lvl, boolean open) {
        if (indicator != null)
            lvl.setBlock(indicator, (open ? Blocks.VERDANT_FROGLIGHT : Blocks.REDSTONE_BLOCK).defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    public Gate(BlockPos a, BlockPos b, Palette closed) {
        this.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        this.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        this.closed = closed;
    }

    /** Состояние берётся из мира: после перезагрузки флаги в памяти ничего не знают. */
    public boolean isOpen(ServerLevel lvl) {
        return lvl.getBlockState(min).isAir();
    }

    public AABB box() {
        return new AABB(min, max.offset(1, 1, 1));
    }

    /** Мгновенно, без звука (для постройки/восстановления). */
    public void setInstant(ServerLevel lvl, boolean open) {
        indicate(lvl, open);
        RandomSource r = RandomSource.create(min.asLong());
        for (BlockPos p : BlockPos.betweenClosed(min, max))
            lvl.setBlock(p, open ? Blocks.AIR.defaultBlockState() : closed.pick(r), Block.UPDATE_CLIENTS);
    }

    public void open(Complex cx, Room owner) {
        ServerLevel lvl = cx.level();
        if (isOpen(lvl) || cx.tick() < busyUntil) return;
        indicate(lvl, true);
        BlockPos c = BlockPos.containing(box().getCenter());
        lvl.playSound(null, c, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.2f, 0.6f);
        lvl.playSound(null, c, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 0.5f);
        int rows = max.getY() - min.getY() + 1;
        busyUntil = cx.tick() + rows * 4L + 2;
        for (int i = 0; i < rows; i++) {
            final int y = min.getY() + i;
            cx.later(owner, i * 4, () -> {
                for (BlockPos p : BlockPos.betweenClosed(new BlockPos(min.getX(), y, min.getZ()), new BlockPos(max.getX(), y, max.getZ())))
                    lvl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                lvl.playSound(null, c, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.5f, 0.6f);
            });
        }
    }

    public void close(Complex cx, Room owner) {
        ServerLevel lvl = cx.level();
        if (!isOpen(lvl) || cx.tick() < busyUntil) return;
        indicate(lvl, false);
        BlockPos c = BlockPos.containing(box().getCenter());
        lvl.playSound(null, c, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2f, 0.6f);
        RandomSource r = RandomSource.create(min.asLong());
        int rows = max.getY() - min.getY() + 1;
        busyUntil = cx.tick() + rows * 3L + 2;
        for (int i = 0; i < rows; i++) {
            final int y = max.getY() - i;
            cx.later(owner, i * 3, () -> {
                for (BlockPos p : BlockPos.betweenClosed(new BlockPos(min.getX(), y, min.getZ()), new BlockPos(max.getX(), y, max.getZ())))
                    lvl.setBlock(p, closed.pick(r), Block.UPDATE_CLIENTS);
                lvl.playSound(null, c, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.6f, 0.6f);
            });
        }
    }
}
