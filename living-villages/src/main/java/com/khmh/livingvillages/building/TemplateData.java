package com.khmh.livingvillages.building;

import com.khmh.livingvillages.LivingVillages;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A structure template read into a plain block list, ordered the way a builder would place it:
 * clearing first, then solid blocks bottom-up, then attached blocks (torches, doors, beds...), then fluids.
 * Two-block pieces (doors, beds) are kept next to each other in the order.
 */
public final class TemplateData {
    public record Entry(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
    }

    private static final Map<ResourceLocation, Optional<TemplateData>> CACHE = new ConcurrentHashMap<>();

    private final Vec3i size;
    private final List<Entry> blocks;
    private final BlockPos entrance;
    private final Map<CostKey, Integer> cost;

    private TemplateData(Vec3i size, List<Entry> blocks, BlockPos entrance) {
        this.size = size;
        this.blocks = List.copyOf(blocks);
        this.entrance = entrance;
        this.cost = MaterialCost.total(this.blocks);
    }

    public Vec3i size() {
        return size;
    }

    public List<Entry> blocks() {
        return blocks;
    }

    /** Local position just outside the door (or the street connection of a vanilla piece). */
    public BlockPos entrance() {
        return entrance;
    }

    public Map<CostKey, Integer> cost() {
        return cost;
    }

    public static Optional<TemplateData> get(ServerLevel level, BuildingType type) {
        return CACHE.computeIfAbsent(type.template(), id -> load(level, type));
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static Optional<TemplateData> load(ServerLevel level, BuildingType type) {
        Optional<StructureTemplate> template = level.getStructureManager().get(type.template());
        if (template.isEmpty()) {
            LivingVillages.LOGGER.error("Missing structure template {} for building {}", type.template(), type.id());
            return Optional.empty();
        }
        try {
            return Optional.of(parse(level, template.get().save(new CompoundTag()), type));
        } catch (RuntimeException e) {
            LivingVillages.LOGGER.error("Could not read structure template {}", type.template(), e);
            return Optional.empty();
        }
    }

    private static TemplateData parse(ServerLevel level, CompoundTag tag, BuildingType type) {
        HolderLookup<Block> lookup = level.holderLookup(Registries.BLOCK);
        ListTag sizeTag = tag.getList("size", Tag.TAG_INT);
        Vec3i size = new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        ListTag paletteTag = tag.contains("palettes", Tag.TAG_LIST)
                ? tag.getList("palettes", Tag.TAG_LIST).getList(0)
                : tag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(lookup, paletteTag.getCompound(i)));
        }

        List<Entry> entries = new ArrayList<>();
        BlockPos streetEntrance = null;
        ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag b = blocksTag.getCompound(i);
            ListTag p = b.getList("pos", Tag.TAG_INT);
            BlockPos pos = new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2));
            BlockState state = palette.get(b.getInt("state"));
            CompoundTag nbt = b.contains("nbt", Tag.TAG_COMPOUND) ? b.getCompound("nbt") : null;

            if (state.is(Blocks.JIGSAW)) {
                if (nbt != null && nbt.getString("pool").endsWith("streets") && streetEntrance == null) {
                    Direction front = state.getValue(JigsawBlock.ORIENTATION).front();
                    streetEntrance = pos.relative(front);
                }
                state = nbt == null ? Blocks.AIR.defaultBlockState() : parseState(lookup, nbt.getString("final_state"));
                nbt = null;
            }
            if (state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)) {
                continue;
            }
            if (type.vanilla() && state.isAir()) {
                continue; // village generation never places air
            }
            if (!type.vanilla() && pos.getY() == type.groundLayer()
                    && (state.isAir() || state.is(Blocks.GRASS_BLOCK))) {
                continue; // keep the natural ground where the building has no foundation
            }
            entries.add(new Entry(pos, state, nbt));
        }

        BlockPos entrance = streetEntrance != null ? streetEntrance : doorEntrance(entries, size, type.groundLayer());
        return new TemplateData(size, order(entries), entrance);
    }

    private static BlockState parseState(HolderLookup<Block> lookup, String s) {
        try {
            return BlockStateParser.parseForBlock(lookup, s, false).blockState();
        } catch (CommandSyntaxException e) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    /** Just outside the lowest door, on the side of the bounding box the door is closest to. */
    private static BlockPos doorEntrance(List<Entry> entries, Vec3i size, int groundLayer) {
        Entry door = entries.stream()
                .filter(e -> e.state().getBlock() instanceof DoorBlock
                        && e.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                .min(Comparator.comparingInt(e -> e.pos().getY()))
                .orElse(null);
        if (door == null) {
            return new BlockPos(size.getX() / 2, groundLayer + 1, size.getZ());
        }
        BlockPos d = door.pos();
        int west = d.getX(), east = size.getX() - 1 - d.getX(), north = d.getZ(), south = size.getZ() - 1 - d.getZ();
        int min = Math.min(Math.min(west, east), Math.min(north, south));
        if (min == south) {
            return new BlockPos(d.getX(), d.getY(), size.getZ());
        } else if (min == north) {
            return new BlockPos(d.getX(), d.getY(), -1);
        } else if (min == west) {
            return new BlockPos(-1, d.getY(), d.getZ());
        }
        return new BlockPos(size.getX(), d.getY(), d.getZ());
    }

    private static int phase(BlockState s) {
        if (s.isAir()) {
            return 0;
        }
        if (!s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            return 3;
        }
        if (!s.isSolid() || s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || s.hasProperty(BlockStateProperties.BED_PART)
                || s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && s.is(Blocks.LADDER)) {
            return 2;
        }
        return 1;
    }

    private static List<Entry> order(List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.<Entry>comparingInt(e -> phase(e.state()))
                .thenComparingInt(e -> e.pos().getY())
                .thenComparingInt(e -> e.pos().getZ())
                .thenComparingInt(e -> e.pos().getX()));
        Map<BlockPos, Entry> byPos = new HashMap<>();
        sorted.forEach(e -> byPos.put(e.pos(), e));
        List<Entry> out = new ArrayList<>(sorted.size());
        for (Entry e : sorted) {
            BlockPos first = firstHalfOf(e.state(), e.pos());
            if (first != null && byPos.containsKey(first)) {
                continue; // goes right after its first half
            }
            out.add(e);
            BlockPos partner = partnerOf(e.state(), e.pos());
            if (partner != null) {
                Entry other = byPos.get(partner);
                if (other != null && firstHalfOf(other.state(), partner) != null) {
                    out.add(other);
                }
            }
        }
        return out;
    }

    @Nullable
    private static BlockPos partnerOf(BlockState s, BlockPos pos) {
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            return pos.above();
        }
        if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.FOOT) {
            return pos.relative(s.getValue(BedBlock.FACING));
        }
        return null;
    }

    /** For the second half of a door or bed: where its first half is; null for anything else. */
    @Nullable
    private static BlockPos firstHalfOf(BlockState s, BlockPos pos) {
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) {
            return pos.relative(s.getValue(BedBlock.FACING).getOpposite());
        }
        return null;
    }
}
