package com.khmh.livingvillages.village;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

/**
 * Real harvest: vanilla farmers really farm the fields, but keep the crops in their pockets. Whatever they hold
 * beyond what a villager needs (food for breeding, seeds for replanting) goes to the village stockpile.
 */
public final class Gathering {
    /** Item -> how many a villager keeps for itself. */
    private static final Map<Item, Integer> KEEP = Map.of(
            Items.WHEAT, 6, Items.BREAD, 6, Items.CARROT, 6, Items.POTATO, 6, Items.BEETROOT, 6);

    private Gathering() {
    }

    static void collectHarvest(Village v, List<Villager> villagers) {
        int cap = v.storageCap();
        for (Villager villager : villagers) {
            SimpleContainer inv = villager.getInventory();
            for (Map.Entry<Item, Integer> keep : KEEP.entrySet()) {
                Item item = keep.getKey();
                int surplus = inv.countItem(item) - keep.getValue();
                int room = cap - v.storage().count(item);
                int take = Math.min(surplus, room);
                if (take <= 0) {
                    continue;
                }
                v.storage().add(item, take);
                for (int i = 0; i < inv.getContainerSize() && take > 0; i++) {
                    ItemStack stack = inv.getItem(i);
                    if (stack.is(item)) {
                        int n = Math.min(take, stack.getCount());
                        stack.shrink(n);
                        take -= n;
                    }
                }
                inv.setChanged();
            }
        }
    }
}
