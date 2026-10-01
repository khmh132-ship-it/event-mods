package com.khmh.livingvillages.building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Lays a dirt path from a building's entrance towards the bell until it meets an existing path. */
public final class PathBuilder {
    private PathBuilder() {
    }

    public static void connect(ServerLevel level, BlockPos from, BlockPos to, int maxLength) {
        int x = from.getX(), z = from.getZ();
        int length = 0;
        boolean first = true;
        while (length++ < maxLength) {
            if (Math.abs(x - to.getX()) <= 2 && Math.abs(z - to.getZ()) <= 2) {
                return;
            }
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                return;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos ground = new BlockPos(x, top, z);
            BlockState state = level.getBlockState(ground);
            if (state.is(Blocks.DIRT_PATH) && !first) {
                return; // joined the network
            }
            first = false;
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                    || state.is(Blocks.PODZOL)) {
                BlockState above = level.getBlockState(ground.above());
                if (above.is(BlockTags.REPLACEABLE) && !above.isAir() && above.getFluidState().isEmpty()) {
                    level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                if (level.getBlockState(ground.above()).isAir()) {
                    level.setBlock(ground, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            // Walk the longer axis first so paths look like streets, not staircases.
            int dx = Integer.signum(to.getX() - x), dz = Integer.signum(to.getZ() - z);
            if (Math.abs(to.getX() - x) >= Math.abs(to.getZ() - z)) {
                x += dx;
            } else {
                z += dz;
            }
        }
    }
}
