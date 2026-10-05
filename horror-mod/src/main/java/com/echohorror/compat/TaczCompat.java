package com.echohorror.compat;

import com.echohorror.Config;
import com.echohorror.EchoHorror;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Timeless and Classics Zero, if present: guns cannot be made, only found — a pistol and a double-barrel shotgun.
 * The gun smith table can still make the two kinds of ammo they use. No compile-time dependency on TACZ.
 */
public final class TaczCompat {
    public static final String PISTOL = "tacz:cz75", SHOTGUN = "tacz:db_long";
    public static final String AMMO_PISTOL = "tacz:9mm", AMMO_SHOTGUN = "tacz:12g";
    private static final Set<String> KEEP_AMMO = Set.of(AMMO_PISTOL, AMMO_SHOTGUN);

    private TaczCompat() {}

    public static boolean active() {
        try {
            return ModList.get().isLoaded("tacz") && Config.TACZ.get();
        } catch (Exception e) {
            return false;
        }
    }

    private static Item item(String path) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", path));
        return it == null ? Items.AIR : it;
    }

    /** A found gun, partly loaded. */
    public static ItemStack gun(String gunId, int loaded) {
        if (!active()) return ItemStack.EMPTY;
        Item it = item("modern_kinetic_gun");
        if (it == Items.AIR) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(it);
        CompoundTag t = s.getOrCreateTag();
        t.putString("GunId", gunId);
        t.putString("GunFireMode", "SEMI");
        t.putInt("GunCurrentAmmoCount", loaded);
        t.putBoolean("HasBulletInBarrel", false);
        return s;
    }

    public static ItemStack ammo(String ammoId, int count) {
        if (!active()) return ItemStack.EMPTY;
        Item it = item("ammo");
        if (it == Items.AIR) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(it, count);
        s.getOrCreateTag().putString("AmmoId", ammoId);
        return s;
    }

    /** Removes every TACZ gun smith recipe except the two ammo types. */
    public static void filterRecipes(MinecraftServer server) {
        if (!active()) return;
        RecipeManager rm = server.getRecipeManager();
        List<Recipe<?>> keep = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        int removed = 0;
        for (Recipe<?> r : rm.getRecipes()) {
            ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
            if (type != null && "tacz".equals(type.getNamespace())) {
                ItemStack res;
                try {
                    res = r.getResultItem(server.registryAccess());
                } catch (Exception e) {
                    res = ItemStack.EMPTY;
                }
                String ammoId = res.hasTag() ? res.getTag().getString("AmmoId") : "";
                boolean isAmmo = res.getItem() == item("ammo") || r.getId().getPath().startsWith("ammo/");
                if (ammoId.isEmpty()) { // fall back to the recipe's file name: tacz:ammo/9mm
                    String p = r.getId().getPath();
                    ammoId = "tacz:" + p.substring(p.lastIndexOf('/') + 1);
                }
                if (!(isAmmo && KEEP_AMMO.contains(ammoId))) {
                    removed++;
                    continue;
                }
                kept.add(r.getId().toString());
            }
            keep.add(r);
        }
        if (removed > 0) {
            rm.replaceRecipes(keep);
            EchoHorror.LOG.info("TACZ: removed {} gun smith recipes; guns must be found; kept {}", removed, kept);
        }
    }
}
