package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.block.LVBlocks;
import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.economy.CraftPlanner;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.function.Predicate;

/** Finds the crafting tables and furnaces of a village (in its buildings or set up near the bell). */
final class Stations {
    private static final int AROUND_BELL = 8;

    private Stations() {
    }

    @Nullable
    static BlockPos find(ServerLevel level, Village village, CraftPlanner.Station station, BlockPos near) {
        Predicate<BlockPos> match = switch (station) {
            case HAND -> p -> false;
            case TABLE -> p -> {
                BlockState s = level.getBlockState(p);
                return s.is(Blocks.CRAFTING_TABLE) || s.is(LVBlocks.APPRENTICE_WORKBENCH.get());
            };
            case FURNACE -> p -> level.getBlockEntity(p) instanceof FurnaceBlockEntity;
        };
        BlockPos best = null;
        for (Building b : village.buildings()) {
            if (b.isComplete()) {
                best = closer(best, scan(level, b.box(), match, near), near);
            }
        }
        BlockPos c = village.center();
        best = closer(best, scan(level, new BoundingBox(c.getX() - AROUND_BELL, c.getY() - 3, c.getZ() - AROUND_BELL,
                c.getX() + AROUND_BELL, c.getY() + 3, c.getZ() + AROUND_BELL), match, near), near);
        return best;
    }

    @Nullable
    private static BlockPos scan(ServerLevel level, BoundingBox box, Predicate<BlockPos> match, BlockPos near) {
        if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
            return null;
        }
        return BlockPos.betweenClosedStream(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                .filter(match).map(BlockPos::immutable)
                .min(Comparator.comparingDouble(p -> p.distSqr(near))).orElse(null);
    }

    private static BlockPos closer(@Nullable BlockPos a, @Nullable BlockPos b, BlockPos near) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.distSqr(near) <= b.distSqr(near) ? a : b;
    }

    /** A free spot on the ground near the bell where a new table or furnace can be set up. */
    @Nullable
    static BlockPos spotNearBell(ServerLevel level, Village village) {
        BlockPos c = village.center();
        for (int r = 2; r <= AROUND_BELL; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.offset(dx, 0, dz));
                    BlockState ground = level.getBlockState(top.below());
                    if (Math.abs(top.getY() - c.getY()) <= 2 && ground.isSolidRender(level, top.below())
                            && level.getBlockState(top).canBeReplaced() && village.buildings().stream()
                            .noneMatch(b -> b.box().inflatedBy(1).isInside(top))) {
                        return top;
                    }
                }
            }
        }
        return null;
    }
}
