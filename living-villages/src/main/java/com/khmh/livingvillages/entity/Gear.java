package com.khmh.livingvillages.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * How good a tool, weapon or piece of armour is for a villager: the material first (wood, stone, iron, diamond,
 * netherite), then its enchantments. The best a villager can have is enchanted diamond (or netherite).
 */
public final class Gear {
    private static final String[] MATERIALS = {"wooden_", "golden_", "stone_", "leather_", "chainmail_", "iron_",
            "diamond_", "netherite_"};

    private Gear() {
    }

    public static int score(ItemStack s) {
        int base;
        if (s.getItem() instanceof TieredItem t) {
            base = t.getTier().getLevel() * 100 + (int) t.getTier().getSpeed();
        } else if (s.getItem() instanceof ArmorItem a) {
            base = a.getDefense() * 30 + (int) (a.getToughness() * 20);
        } else {
            return 0;
        }
        int magic = EnchantmentHelper.getEnchantments(s).values().stream().mapToInt(Integer::intValue).sum();
        return base + magic * 15;
    }

    public static int score(Item item) {
        return score(new ItemStack(item));
    }

    /** The same kind of tool or armour in another material ("iron_" for a stone pickaxe), or null. */
    @Nullable
    public static Item inMaterial(Item item, String material) {
        String path = ForgeRegistries.ITEMS.getKey(item).getPath();
        for (String m : MATERIALS) {
            if (path.startsWith(m)) {
                Item other = ForgeRegistries.ITEMS.getValue(new ResourceLocation(material + path.substring(m.length())));
                return other == null || other == Items.AIR ? null : other;
            }
        }
        return null;
    }
}
