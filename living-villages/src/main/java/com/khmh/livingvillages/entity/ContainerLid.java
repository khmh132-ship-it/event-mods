package com.khmh.livingvillages.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Opens and closes chests and barrels the way a player does: lid animation and sound. */
final class ContainerLid {
    private ContainerLid() {
    }

    static void open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BarrelBlock) {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        } else {
            level.blockEvent(pos, state.getBlock(), 1, 1);
            level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
    }

    static void close(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BarrelBlock) {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
        } else {
            level.blockEvent(pos, state.getBlock(), 1, 0);
            level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
    }
}
