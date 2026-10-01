package com.khmh.livingvillages.building;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * A kind of building a village can construct.
 *
 * @param group       buildings of one group are interchangeable for planning (e.g. all small houses)
 * @param vanilla     a vanilla village piece: placed one block above the ground, air skipped, jigsaws resolved
 * @param groundLayer template layer that replaces the ground surface (custom buildings only)
 * @param minLevel    village level needed before the planner picks it
 * @param produces    items added to the stockpile per production cycle once the building is complete
 */
public record BuildingType(String id, String group, ResourceLocation template, boolean vanilla, int groundLayer,
                           int minLevel, Map<Item, Double> produces) {
}
