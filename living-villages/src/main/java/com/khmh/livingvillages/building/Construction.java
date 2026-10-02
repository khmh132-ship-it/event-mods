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

import com.khmh.livingvillages.stock.Stock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
            if (!catchingUp) { // nobody builds by magic: without a builder the site simply waits
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
            Stock pay = village.stock(level);
            for (int i = 0; i < steps && !b.isComplete(); i++) {
                if (!payFor(level, b, pay)) {
                    b.setLastStep(now); // out of materials: wait, do not pile up a backlog
                    break;
                }
                step(level, village, b);
            }
            village.markDirty();
        }
    }

    /** Fractional pooled costs owed per building by steps nobody carried materials for. */
    private static final Map<UUID, Map<CostKey, Double>> DEBT = new HashMap<>();

    /**
     * Pays for the next step out of the village stock at pooled prices (wood, stone...). Used when blocks are
     * placed without a builder carrying them: unloaded time, or a village with nobody to build.
     */
    private static boolean payFor(ServerLevel level, Building b, Stock stock) {
        if (b.isFree() || b.phase() != Building.Phase.BUILD) {
            return true;
        }
        Next next = next(level, b);
        if (next == null) {
            return true;
        }
        Map<CostKey, Double> debt = DEBT.computeIfAbsent(b.id(), k -> new HashMap<>());
        MaterialCost.of(next.state()).forEach((k, v) -> debt.merge(k, v, Double::sum));
        for (Map.Entry<CostKey, Double> e : debt.entrySet()) {
            int whole = (int) Math.floor(e.getValue());
            if (whole > 0) {
                if (e.getKey().available(stock) < whole) {
                    MaterialCost.of(next.state()).forEach((k, v) -> debt.merge(k, -v, Double::sum));
                    return false;
                }
                e.getKey().take(stock, whole);
                e.setValue(e.getValue() - whole);
            }
        }
        return true;
    }

    /** The next block to place, already turned the building's way, and the item a builder needs for it. */
    public record Next(BlockPos pos, BlockState state, @Nullable Item item) {
    }

    @Nullable
    public static Next next(ServerLevel level, Building b) {
        if (b.phase() != Building.Phase.BUILD) {
            return null;
        }
        TemplateData data = TemplateData.get(level, b.type()).orElse(null);
        if (data == null || b.progress() >= data.blocks().size()) {
            return null;
        }
        TemplateData.Entry e = data.blocks().get(b.progress());
        BlockState state = e.state().rotate(b.rotation());
        return new Next(Placement.toWorld(b.origin(), b.rotation(), e.pos()), state,
                b.isFree() ? null : requiredItem(state));
    }

    /** What a builder must carry to place this block; null for blocks that cost nothing (air, ground, water...). */
    @Nullable
    public static Item requiredItem(BlockState state) {
        if (MaterialCost.of(state).isEmpty()) {
            return null;
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
            return Items.WHITE_BED; // any bed will do; the colour is the template's
        }
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? null : item;
    }

    /** Items needed for the next {@code count} blocks, in order of need. */
    public static Map<Item, Integer> lookahead(ServerLevel level, Building b, int count) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        if (b.phase() != Building.Phase.BUILD || b.isFree()) {
            return out;
        }
        TemplateData data = TemplateData.get(level, b.type()).orElse(null);
        if (data == null) {
            return out;
        }
        for (int i = b.progress(); i < Math.min(data.blocks().size(), b.progress() + count); i++) {
            Item item = requiredItem(data.blocks().get(i).state());
            if (item != null) {
                out.merge(item, 1, Integer::sum);
            }
        }
        return out;
    }

    /**
     * The builder places the next block (he has already taken the item out of his inventory). With
     * {@code substitute} he puts that block instead, or with {@code skip} leaves the spot as it is.
     */
    public static void placeByBuilder(ServerLevel level, Village village, Building b, long now,
                                      @Nullable BlockState substitute, boolean skip) {
        if (b.isComplete()) {
            return;
        }
        if (b.phase() == Building.Phase.BUILD && (substitute != null || skip)) {
            TemplateData data = TemplateData.get(level, b.type()).orElse(null);
            Next next = next(level, b);
            if (data != null && next != null) {
                b.advance();
                if (!skip) {
                    level.setBlock(next.pos(), substitute, Block.UPDATE_ALL);
                }
                if (b.progress() >= data.blocks().size()) {
                    b.nextPhase();
                    village.onBuildingComplete(level, b);
                }
            }
        } else {
            step(level, village, b);
        }
        b.setLastStep(now);
        village.markDirty();
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

    /** Finishes a building at once (debug); nothing is paid. */
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
        BlockState old = level.getBlockState(pos);
        if (state.isAir() && !old.isAir() && old.getFluidState().isEmpty() && SiteFinder.isNatural(old)
                && !old.is(net.minecraft.tags.BlockTags.DIRT)) {
            // Digging out: stone, sand, gravel, clay and ore go to the village store (dirt is just left aside).
            for (net.minecraft.world.item.ItemStack drop : Block.getDrops(old, level, pos, null, null, new net.minecraft.world.item.ItemStack(Items.IRON_PICKAXE))) {
                village.storage().add(drop.getItem(), drop.getCount());
            }
        }
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

    /** One block of site work for a builder: something to break (top down), or a dip to fill with dirt. */
    public record Prep(BlockPos pos, boolean fill) {
    }

    /**
     * The next block of levelling, column by column: the highest thing above the ground in the column comes off
     * first (so nothing falls on anyone), then dips get filled. Finished columns are passed over; null once the
     * site is level (the building moves on to construction).
     */
    @Nullable
    public static Prep nextPrep(ServerLevel level, Building b) {
        if (b.phase() != Building.Phase.PREPARE) {
            return null;
        }
        BoundingBox box = b.box();
        int w = box.getXSpan();
        int columns = w * box.getZSpan();
        while (b.progress() < columns) {
            int x = box.minX() + b.progress() % w;
            int z = box.minZ() + b.progress() / w;
            int ground = b.groundY();
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            for (int y = Math.max(top, box.maxY()); y > ground; y--) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(p);
                if (!s.isAir() && s.getFluidState().isEmpty()) {
                    return new Prep(p, false);
                }
            }
            if (top < ground) {
                return new Prep(new BlockPos(x, top + 1, z), true);
            }
            b.advance();
        }
        b.nextPhase();
        LivingVillages.LOGGER.debug("Site of {} levelled", b.typeId());
        return null;
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
