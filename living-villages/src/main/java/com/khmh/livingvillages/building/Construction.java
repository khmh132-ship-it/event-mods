package com.khmh.livingvillages.building;

import com.khmh.livingvillages.LivingVillages;
import com.khmh.livingvillages.config.LVConfig;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Advances buildings under construction. Each step levels one ground column or places one block. While a site
 * is not loaded, steps pile up and are placed (quickly, capped per second) once it is loaded again, so a village
 * keeps building while nobody watches.
 */
public final class Construction {
    private Construction() {
    }

    /** Called once per second for every village. */
    public static void tick(ServerLevel level, Village village, long now, double speed) {
        for (Building b : village.buildings()) {
            if (b.isComplete()) {
                continue;
            }
            BoundingBox box = b.box();
            if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
                continue; // backlog keeps growing
            }
            int interval = Math.max(1, (int) Math.round(LVConfig.BUILD_INTERVAL.get() / Math.max(0.01, speed)));
            long due = (now - b.lastStep()) / interval;
            int steps = (int) Math.min(due, LVConfig.MAX_BUILD_STEPS_PER_SECOND.get());
            if (steps <= 0) {
                continue;
            }
            b.setLastStep(b.lastStep() + (long) steps * interval); // anything over the cap stays as backlog
            for (int i = 0; i < steps && !b.isComplete(); i++) {
                step(level, village, b);
            }
            village.markDirty();
        }
    }

    /** Finishes a building at once (debug). */
    public static void finish(ServerLevel level, Village village, Building b) {
        while (!b.isComplete()) {
            step(level, village, b);
        }
        village.markDirty();
    }

    private static void step(ServerLevel level, Village village, Building b) {
        if (b.phase() == Building.Phase.PREPARE) {
            prepareColumn(level, b);
            return;
        }
        TemplateData data = TemplateData.get(level, b.type()).orElse(null);
        if (data == null || b.progress() >= data.blocks().size()) {
            b.nextPhase();
            if (b.isComplete()) {
                village.onBuildingComplete(level, b);
            }
            return;
        }
        TemplateData.Entry e = data.blocks().get(b.progress());
        b.advance();
        BlockPos pos = Placement.toWorld(b.origin(), b.rotation(), e.pos());
        BlockState state = e.state().rotate(b.rotation());
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (e.nbt() != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                CompoundTag tag = e.nbt().copy();
                tag.putInt("x", pos.getX());
                tag.putInt("y", pos.getY());
                tag.putInt("z", pos.getZ());
                be.load(tag);
                be.setChanged();
            }
        }
        if (b.progress() >= data.blocks().size()) {
            b.nextPhase();
            village.onBuildingComplete(level, b);
        }
    }

    /** Levels one column of the site: fills dips with dirt and clears everything above the ground. */
    private static void prepareColumn(ServerLevel level, Building b) {
        BoundingBox box = b.box();
        int w = box.getXSpan();
        int columns = w * box.getZSpan();
        if (b.progress() >= columns) {
            b.nextPhase();
            return;
        }
        int x = box.minX() + b.progress() % w;
        int z = box.minZ() + b.progress() / w;
        b.advance();
        int ground = b.groundY();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = ground + 1; y <= Math.max(top, box.maxY()); y++) {
            pos.set(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        if (top < ground) {
            for (int y = top + 1; y < ground; y++) {
                level.setBlock(pos.set(x, y, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            level.setBlock(pos.set(x, ground, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        if (b.progress() >= columns) {
            b.nextPhase();
            LivingVillages.LOGGER.debug("Site of {} levelled", b.typeId());
        }
    }
}
