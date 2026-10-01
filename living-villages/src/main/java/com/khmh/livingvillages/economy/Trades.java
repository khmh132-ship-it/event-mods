package com.khmh.livingvillages.economy;

import com.khmh.livingvillages.entity.WorkerJob;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Which craftsman makes what. Specialists take the orders of their trade; the apprentice takes everything no
 * specialist in the village covers.
 */
public final class Trades {
    private static final List<String> STONE_WORDS = List.of("stone", "cobble", "brick", "andesite", "diorite",
            "granite", "deepslate", "sandstone", "tuff", "blackstone", "basalt", "quartz", "prismarine", "terracotta");

    private Trades() {
    }

    /** The specialist trade an item belongs to; APPRENTICE if none. */
    public static WorkerJob tradeOf(Item item) {
        ItemStack s = new ItemStack(item);
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (s.is(ItemTags.SWORDS) || item == Items.SHIELD) {
            return WorkerJob.WEAPONSMITH;
        }
        if (s.is(ItemTags.PICKAXES) || s.is(ItemTags.AXES) || s.is(ItemTags.SHOVELS) || s.is(ItemTags.HOES)
                || item == Items.SHEARS || item == Items.FISHING_ROD || item == Items.FLINT_AND_STEEL) {
            return WorkerJob.TOOLSMITH;
        }
        if (item instanceof ArmorItem a) {
            return a.getMaterial() == ArmorMaterials.LEATHER ? WorkerJob.LEATHERWORKER : WorkerJob.ARMORER;
        }
        if (item == Items.BOW || item == Items.CROSSBOW || s.is(ItemTags.ARROWS) || item == Items.FLINT
                || item == Items.STRING) {
            return WorkerJob.FLETCHER;
        }
        if (item == Items.LEATHER || item == Items.BOOK || item == Items.ITEM_FRAME || item == Items.SADDLE) {
            return WorkerJob.LEATHERWORKER;
        }
        if (s.is(ItemTags.PLANKS) || s.is(ItemTags.WOODEN_STAIRS) || s.is(ItemTags.WOODEN_SLABS)
                || s.is(ItemTags.WOODEN_FENCES) || s.is(ItemTags.FENCE_GATES) || s.is(ItemTags.WOODEN_DOORS)
                || s.is(ItemTags.WOODEN_TRAPDOORS) || s.is(ItemTags.SIGNS) || item == Items.STICK
                || item == Items.CHEST || item == Items.BARREL || item == Items.LADDER || item == Items.CRAFTING_TABLE
                || item == Items.BOOKSHELF || s.is(ItemTags.WOODEN_PRESSURE_PLATES) || s.is(ItemTags.WOODEN_BUTTONS)) {
            return WorkerJob.CARPENTER;
        }
        if (item == Items.GLASS || item == Items.GLASS_PANE || item == Items.GRAVEL || item == Items.SAND
                || STONE_WORDS.stream().anyMatch(path::contains) || item == Items.FURNACE) {
            return WorkerJob.MASON;
        }
        return WorkerJob.APPRENTICE;
    }

    /** Whether a craftsman of {@code job} should take an order for {@code item} in this village. */
    public static boolean isMine(WorkerJob job, Item item, Village village) {
        WorkerJob trade = tradeOf(item);
        if (job == WorkerJob.APPRENTICE) {
            return trade == WorkerJob.APPRENTICE || village.professions().getOrDefault(trade.name().toLowerCase(), 0) == 0;
        }
        return job == trade;
    }
}
