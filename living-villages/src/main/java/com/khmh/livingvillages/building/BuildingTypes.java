package com.khmh.livingvillages.building;

import com.khmh.livingvillages.LivingVillages;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Catalog of everything a plains village can build. */
public final class BuildingTypes {
    private static final Map<String, BuildingType> BY_ID = new LinkedHashMap<>();

    static {
        // Living Villages buildings (templates in data/livingvillages/structures/plains/).
        custom("lumberjack_hut", "lumberjack", 1, 0, Map.of(Items.OAK_LOG, 4.0, Items.OAK_SAPLING, 0.25));
        custom("warehouse", "warehouse", 1, 0, Map.of());
        custom("builder_workshop", "builder", 1, 0, Map.of());
        custom("quest_board", "quest_board", 1, 0, Map.of());
        custom("apprentice_workshop", "apprentice", 1, 0, Map.of());
        custom("mine", "mine", 1, 6, Map.of(Items.COBBLESTONE, 4.0, Items.COAL, 0.75, Items.IRON_INGOT, 0.5,
                Items.SAND, 1.0));
        custom("sawmill", "sawmill", 2, 0, Map.of(Items.OAK_LOG, 2.0));
        custom("watchtower", "watchtower", 2, 0, Map.of());
        custom("barracks", "barracks", 3, 0, Map.of());
        custom("golem_pad", "golem_pad", 3, 0, Map.of());
        custom("town_hall", "town_hall", 3, 0, Map.of());
        custom("tavern", "tavern", 3, 0, Map.of());
        // Walls need a perimeter planner; they can be built by command only for now.
        custom("wall_tower", "wall", 99, 0, Map.of());
        custom("wall_straight", "wall", 99, 0, Map.of());
        custom("wall_gate", "wall", 99, 0, Map.of());

        // Vanilla plains village pieces.
        for (int i = 1; i <= 8; i++) {
            vanilla("small_house_" + i, "house", 1, Map.of());
        }
        vanilla("medium_house_1", "house", 2, Map.of());
        vanilla("medium_house_2", "house", 2, Map.of());
        vanilla("big_house_1", "house", 3, Map.of());
        vanilla("small_farm_1", "farm", 1, Map.of(Items.WHEAT, 2.0, Items.CARROT, 0.5));
        vanilla("large_farm_1", "farm", 2, Map.of(Items.WHEAT, 3.0, Items.CARROT, 1.0, Items.POTATO, 1.0));
        vanilla("animal_pen_1", "pen", 2, Map.of(Items.WHITE_WOOL, 1.0, Items.BEEF, 0.5));
        vanilla("animal_pen_2", "pen", 2, Map.of(Items.WHITE_WOOL, 1.0, Items.BEEF, 0.5));
        vanilla("animal_pen_3", "pen", 2, Map.of(Items.WHITE_WOOL, 1.0, Items.BEEF, 0.5));
        vanilla("stable_1", "stable", 3, Map.of(Items.LEATHER, 1.0));
        vanilla("stable_2", "stable", 3, Map.of(Items.LEATHER, 1.0));
        for (String job : List.of("armorer_house_1", "butcher_shop_1", "butcher_shop_2", "cartographer_1",
                "fisher_cottage_1", "fletcher_house_1", "library_1", "library_2", "masons_house_1",
                "shepherds_house_1", "tannery_1", "temple_3", "temple_4", "tool_smith_1", "weaponsmith_1")) {
            vanilla(job, "workshop", 2, Map.of());
        }
    }

    private BuildingTypes() {
    }

    private static void custom(String id, String group, int minLevel, int groundLayer, Map<Item, Double> produces) {
        BY_ID.put(id, new BuildingType(id, group, new ResourceLocation(LivingVillages.MODID, "plains/" + id), false,
                groundLayer, minLevel, produces));
    }

    private static void vanilla(String id, String group, int minLevel, Map<Item, Double> produces) {
        BY_ID.put(id, new BuildingType(id, group,
                new ResourceLocation("minecraft", "village/plains/houses/plains_" + id), true, 0, minLevel, produces));
    }

    public static BuildingType get(String id) {
        return BY_ID.get(id);
    }

    public static Collection<BuildingType> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static List<BuildingType> inGroup(String group, int maxLevel) {
        List<BuildingType> out = new ArrayList<>();
        for (BuildingType t : BY_ID.values()) {
            if (t.group().equals(group) && t.minLevel() <= maxLevel) {
                out.add(t);
            }
        }
        return out;
    }
}
