package com.khmh.livingvillages.building;

import com.khmh.livingvillages.village.VillageStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What blocks cost in stockpile materials. Deliberately coarse: wood and stone are pooled, ground blocks,
 * plants and fluids are free, and anything not covered here (bells, decorations) is free too.
 */
public final class MaterialCost {
    private static final CostKey WOOD = CostKey.Wood.INSTANCE;
    private static final CostKey STONE = CostKey.Stone.INSTANCE;
    private static final CostKey SAND = new CostKey.Of(Items.SAND);
    private static final CostKey IRON = new CostKey.Of(Items.IRON_INGOT);
    private static final CostKey WOOL = new CostKey.Of(Items.WHITE_WOOL);
    private static final CostKey WHEAT = new CostKey.Of(Items.WHEAT);

    private static final List<String> STONE_WORDS = List.of("cobblestone", "stone_brick", "andesite", "diorite",
            "granite", "deepslate", "tuff", "brick", "smooth_stone", "terracotta", "sandstone");
    private static final List<String> NOT_STONE = List.of("redstone", "glowstone", "lodestone", "dripstone",
            "stonecutter", "grindstone", "nether_brick", "end_stone");

    private MaterialCost() {
    }

    public static Map<CostKey, Integer> total(List<TemplateData.Entry> entries) {
        Map<CostKey, Double> sum = new HashMap<>();
        for (TemplateData.Entry e : entries) {
            of(e.state()).forEach((k, v) -> sum.merge(k, v, Double::sum));
        }
        Map<CostKey, Integer> out = new LinkedHashMap<>();
        sum.forEach((k, v) -> {
            if (v > 0) {
                out.put(k, (int) Math.ceil(v));
            }
        });
        return out;
    }

    public static boolean canAfford(VillageStorage storage, Map<CostKey, Integer> cost) {
        return cost.entrySet().stream().allMatch(e -> e.getKey().available(storage) >= e.getValue());
    }

    public static boolean pay(VillageStorage storage, Map<CostKey, Integer> cost) {
        if (!canAfford(storage, cost)) {
            return false;
        }
        cost.forEach((k, v) -> k.take(storage, v));
        return true;
    }

    public static String describe(Map<CostKey, Integer> cost) {
        StringBuilder sb = new StringBuilder();
        cost.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : ", ").append(v).append(' ').append(k.describe()));
        return sb.isEmpty() ? "free" : sb.toString();
    }

    @SuppressWarnings("deprecation")
    static Map<CostKey, Double> of(BlockState s) {
        if (s.isAir() || s.getBlock() instanceof LiquidBlock || isFree(s) || isSecondHalf(s)) {
            return Map.of();
        }
        Item item = s.getBlock().asItem();
        if (item == Items.AIR) {
            return Map.of();
        }
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();

        if (is(item, ItemTags.LOGS)) return Map.of(WOOD, 4.0);
        if (is(item, ItemTags.PLANKS)) return Map.of(WOOD, 1.0);
        if (is(item, ItemTags.WOODEN_STAIRS)) return Map.of(WOOD, 1.5);
        if (is(item, ItemTags.WOODEN_SLABS)) return Map.of(WOOD, 0.5);
        if (is(item, ItemTags.WOODEN_FENCES)) return Map.of(WOOD, 1.7);
        if (is(item, ItemTags.FENCE_GATES)) return Map.of(WOOD, 4.0);
        if (is(item, ItemTags.WOODEN_DOORS)) return Map.of(WOOD, 2.0);
        if (is(item, ItemTags.WOODEN_TRAPDOORS)) return Map.of(WOOD, 3.0);
        if (is(item, ItemTags.WOODEN_PRESSURE_PLATES)) return Map.of(WOOD, 2.0);
        if (is(item, ItemTags.WOODEN_BUTTONS) || is(item, ItemTags.SIGNS)) return Map.of(WOOD, 1.0);
        if (is(item, ItemTags.BEDS)) return Map.of(WOOD, 3.0, WOOL, 3.0);
        if (is(item, ItemTags.WOOL)) return Map.of(WOOL, 1.0);
        if (is(item, ItemTags.WOOL_CARPETS)) return Map.of(WOOL, 0.67);

        if (item == Items.CHEST || item == Items.TRAPPED_CHEST) return Map.of(WOOD, 8.0);
        if (item == Items.BARREL) return Map.of(WOOD, 7.0);
        if (item == Items.CRAFTING_TABLE || item == Items.CARTOGRAPHY_TABLE || item == Items.FLETCHING_TABLE)
            return Map.of(WOOD, 4.0);
        if (item == Items.BOOKSHELF) return Map.of(WOOD, 6.0);
        if (item == Items.LECTERN) return Map.of(WOOD, 14.0);
        if (item == Items.COMPOSTER) return Map.of(WOOD, 3.5);
        if (item == Items.LADDER || item == Items.TORCH || item == Items.LANTERN || item == Items.LOOM)
            return Map.of(WOOD, 1.0);
        if (item == Items.HAY_BLOCK) return Map.of(WHEAT, 9.0);
        if (item == Items.IRON_BLOCK) return Map.of(IRON, 9.0);
        if (item == Items.ANVIL) return Map.of(IRON, 10.0);
        if (item == Items.IRON_BARS || item == Items.RAIL || item == Items.CAULDRON || item == Items.HOPPER)
            return Map.of(IRON, 1.0);
        if (item == Items.SMITHING_TABLE) return Map.of(WOOD, 4.0, IRON, 2.0);
        if (item == Items.STONECUTTER) return Map.of(STONE, 3.0, IRON, 1.0);
        if (item == Items.BLAST_FURNACE) return Map.of(STONE, 8.0, IRON, 5.0);
        if (item == Items.FURNACE || item == Items.SMOKER) return Map.of(STONE, 8.0);
        if (item == Items.GRINDSTONE) return Map.of(STONE, 1.0, WOOD, 2.0);
        if (path.contains("glass")) return Map.of(SAND, path.contains("pane") ? 0.375 : 1.0);
        if (STONE_WORDS.stream().anyMatch(path::contains) && NOT_STONE.stream().noneMatch(path::contains)
                || item == Items.STONE || item == Items.MOSSY_COBBLESTONE) {
            return Map.of(STONE, path.endsWith("_slab") ? 0.5 : path.endsWith("_stairs") ? 1.5 : 1.0);
        }
        return Map.of();
    }

    private static boolean isFree(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND) || s.is(BlockTags.CROPS)
                || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.REPLACEABLE)
                || s.is(Blocks.GRAVEL) || s.is(BlockTags.SAND) || s.is(Blocks.CLAY) || s.is(Blocks.BELL)
                || s.is(Blocks.SCAFFOLDING);
    }

    private static boolean isSecondHalf(BlockState s) {
        return s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                || s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    @SuppressWarnings("deprecation")
    private static boolean is(Item item, TagKey<Item> tag) {
        return item.builtInRegistryHolder().is(tag);
    }
}
