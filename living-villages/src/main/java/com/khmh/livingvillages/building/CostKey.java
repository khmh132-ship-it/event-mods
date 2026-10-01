package com.khmh.livingvillages.building;

import com.khmh.livingvillages.village.VillageStorage;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Something a building costs: a specific item, or a material any of several items can pay for. */
public interface CostKey {
    int available(VillageStorage storage);

    /** Removes {@code amount}; the caller checks {@link #available} first. */
    void take(VillageStorage storage, int amount);

    String describe();

    record Of(Item item) implements CostKey {
        @Override
        public int available(VillageStorage storage) {
            return storage.count(item);
        }

        @Override
        public void take(VillageStorage storage, int amount) {
            storage.take(item, amount);
        }

        @Override
        public String describe() {
            return BuiltInRegistries.ITEM.getKey(item).getPath();
        }
    }

    /** Wood in planks: any planks count once, any log counts as four planks. */
    enum Wood implements CostKey {
        INSTANCE;

        @Override
        public int available(VillageStorage storage) {
            int total = 0;
            for (Object2IntMap.Entry<Item> e : storage.view().object2IntEntrySet()) {
                if (isPlanks(e.getKey())) {
                    total += e.getIntValue();
                } else if (isLog(e.getKey())) {
                    total += e.getIntValue() * 4;
                }
            }
            return total;
        }

        @Override
        public void take(VillageStorage storage, int amount) {
            int left = amount;
            for (Item item : itemsMatching(storage, true)) {
                int n = Math.min(left, storage.count(item));
                storage.take(item, n);
                left -= n;
            }
            for (Item item : itemsMatching(storage, false)) {
                if (left <= 0) {
                    break;
                }
                int logs = Math.min((left + 3) / 4, storage.count(item));
                storage.take(item, logs);
                left -= logs * 4;
            }
            if (left < 0) {
                storage.add(Items.OAK_PLANKS, -left); // change from the last log
            }
        }

        private static List<Item> itemsMatching(VillageStorage storage, boolean planks) {
            List<Item> out = new ArrayList<>();
            for (Item item : storage.view().keySet()) {
                if (planks ? isPlanks(item) : isLog(item)) {
                    out.add(item);
                }
            }
            return out;
        }

        @SuppressWarnings("deprecation")
        private static boolean isPlanks(Item item) {
            return item.builtInRegistryHolder().is(ItemTags.PLANKS);
        }

        @SuppressWarnings("deprecation")
        private static boolean isLog(Item item) {
            return item.builtInRegistryHolder().is(ItemTags.LOGS);
        }

        @Override
        public String describe() {
            return "wood (planks)";
        }
    }

    /** Stone: cobblestone and other plain stones count the same. */
    enum Stone implements CostKey {
        INSTANCE;

        private static final List<Item> ITEMS = List.of(Items.COBBLESTONE, Items.STONE, Items.COBBLED_DEEPSLATE,
                Items.MOSSY_COBBLESTONE, Items.ANDESITE, Items.DIORITE, Items.GRANITE);

        @Override
        public int available(VillageStorage storage) {
            return ITEMS.stream().mapToInt(storage::count).sum();
        }

        @Override
        public void take(VillageStorage storage, int amount) {
            int left = amount;
            for (Item item : ITEMS) {
                int n = Math.min(left, storage.count(item));
                if (n > 0) {
                    storage.take(item, n);
                    left -= n;
                }
            }
        }

        @Override
        public String describe() {
            return "stone";
        }
    }
}
