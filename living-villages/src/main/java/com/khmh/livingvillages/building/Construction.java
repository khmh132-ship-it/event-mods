package com.khmh.livingvillages.building;

import com.khmh.livingvillages.LivingVillages;
import com.khmh.livingvillages.config.LVConfig;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Advances buildings under construction. Each step levels one ground column or places one block.
 *
 * <p>While a village has a hired builder and the site is loaded, the builder places every block himself
 * ({@link #buildOne}). Without a builder the villagers build slowly on their own. While a site is unloaded, steps
 * pile up and are placed (quickly, capped per second) once it is loaded again, so a village keeps building while
 * nobody watches.
 */
public final class Construction {
    /** Buildings whose site was loaded on the previous tick; anything else is catching up on unloaded time. */
    private static final Set<UUID> LOADED_LAST_TICK = new HashSet<>();

    private Construction() {
    }

    /** Called once per second for every village. */
    public static void tick(ServerLevel level, Village village, long now) {
        boolean builder = village.hasBuilder(now);
        for (Building b : village.buildings()) {
            if (b.isComplete()) {
                continue;
            }
            BoundingBox box = b.box();
            if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
                LOADED_LAST_TICK.remove(b.id());
                continue; // backlog keeps growing
            }
            int interval = LVConfig.BUILD_INTERVAL.get();
            long due = (now - b.lastStep()) / interval;
            boolean catchingUp = !LOADED_LAST_TICK.contains(b.id()) && due > 2;
            // While the village has a builder around, he does all the work himself; blocks only appear on their
            // own for time that passed while the site was unloaded, or in a village with nobody to build.
            if (builder && !catchingUp) {
                LOADED_LAST_TICK.add(b.id());
                b.setLastStep(now);
                continue;
            }
            int steps = (int) Math.min(due, LVConfig.MAX_BUILD_STEPS_PER_SECOND.get());
            if (steps <= 0) {
                LOADED_LAST_TICK.add(b.id());
                continue;
            }
            b.setLastStep(b.lastStep() + (long) steps * interval); // anything over the cap stays as backlog
            if (steps == due) {
                LOADED_LAST_TICK.add(b.id());
            }
            for (int i = 0; i < steps && !b.isComplete(); i++) {
                step(level, village, b);
            }
            village.markDirty();
        }
    }

    /** Where the next step happens: the column being levelled, or the next block to place. */
    @Nullable
    public static BlockPos nextTarget(ServerLevel level, Building b) {
        if (b.isComplete()) {
            return null;
        }
        if (b.phase() == Building.Phase.PREPARE) {
            BoundingBox box = b.box();
            int w = box.getXSpan();
            int i = Math.min(b.progress(), w * box.getZSpan() - 1);
            return new BlockPos(box.minX() + i % w, b.groundY(), box.minZ() + i / w);
        }
        TemplateData data = TemplateData.get(level, b.type()).orElse(null);
        if (data == null || b.progress() >= data.blocks().size()) {
            return b.entrance();
        }
        return Placement.toWorld(b.origin(), b.rotation(), data.blocks().get(b.progress()).pos());
    }

    /** One step done by a builder on site. */
    public static void buildOne(ServerLevel level, Village village, Building b, long now) {
        if (!b.isComplete()) {
            step(level, village, b);
            b.setLastStep(now);
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
            prepareColumn(level, village, b);
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
        if (!state.isAir() && level.random.nextInt(3) == 0) {
            level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6F, 0.9F);
        }
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
    private static void prepareColumn(ServerLevel level, Village village, Building b) {
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
            BlockState old = level.getBlockState(pos);
            if (!old.isAir()) {
                if (old.is(BlockTags.LOGS)) {
                    village.storage().add(old.getBlock().asItem(), 1); // felled timber, moved into the chests later
                }
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
