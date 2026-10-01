package com.khmh.livingvillages.stock;

import net.minecraft.world.item.Item;

import java.util.Map;

/** Something that holds a village's goods: the warehouse chests, or the unplaced buffer. */
public interface Stock {
    int count(Item item);

    /** Removes exactly {@code amount} if available; changes nothing and returns false otherwise. */
    boolean take(Item item, int amount);

    void add(Item item, int amount);

    /** Item totals, for reporting and for pooled costs (any planks, any logs...). */
    Map<Item, Integer> totals();
}
