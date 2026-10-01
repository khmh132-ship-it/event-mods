package com.khmh.livingvillages.village;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Abstract village stockpile. Workers deposit into it, builders and smiths take from it.
 * Items are counted by type only; NBT is intentionally ignored.
 */
public class VillageStorage {
    private final Object2IntOpenHashMap<Item> items = new Object2IntOpenHashMap<>();
    private Runnable onChange = () -> {};

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public int count(Item item) {
        return items.getInt(item);
    }

    public void add(Item item, int amount) {
        if (amount <= 0 || item == Items.AIR) {
            return;
        }
        items.addTo(item, amount);
        onChange.run();
    }

    /** Removes exactly {@code amount} if available. Returns false and changes nothing otherwise. */
    public boolean take(Item item, int amount) {
        int have = items.getInt(item);
        if (amount <= 0 || have < amount) {
            return false;
        }
        if (have == amount) {
            items.removeInt(item);
        } else {
            items.put(item, have - amount);
        }
        onChange.run();
        return true;
    }

    public Object2IntMap<Item> view() {
        return Object2IntMaps.unmodifiable(items);
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Object2IntMap.Entry<Item> e : items.object2IntEntrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", BuiltInRegistries.ITEM.getKey(e.getKey()).toString());
            tag.putInt("count", e.getIntValue());
            list.add(tag);
        }
        return list;
    }

    public void load(ListTag list) {
        items.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue; // item from a removed mod
            }
            int count = tag.getInt("count");
            if (count > 0) {
                items.put(BuiltInRegistries.ITEM.get(id), count);
            }
        }
    }
}
