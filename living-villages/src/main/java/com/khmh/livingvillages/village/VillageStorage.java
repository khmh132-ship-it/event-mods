package com.khmh.livingvillages.village;

import com.khmh.livingvillages.stock.Stock;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The village's buffer: goods that exist but are not in a chest yet (produced while nobody was around, or with
 * no room left). Moved into the warehouse chests by {@link com.khmh.livingvillages.stock.Stockpile#flushBuffer}.
 * Items are counted by type only; NBT is intentionally ignored.
 */
public class VillageStorage implements Stock {
    private final Object2IntOpenHashMap<Item> items = new Object2IntOpenHashMap<>();
    private Runnable onChange = () -> {};

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    public int count(Item item) {
        return items.getInt(item);
    }

    @Override
    public void add(Item item, int amount) {
        if (amount <= 0 || item == Items.AIR) {
            return;
        }
        items.addTo(item, amount);
        onChange.run();
    }

    /** Removes exactly {@code amount} if available. Returns false and changes nothing otherwise. */
    @Override
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

    @Override
    public Map<Item, Integer> totals() {
        return new LinkedHashMap<>(items);
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
