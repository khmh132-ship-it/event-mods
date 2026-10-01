package com.khmh.livingvillages.village;

import com.khmh.livingvillages.LivingVillages;
import com.khmh.livingvillages.config.LVConfig;
import com.khmh.livingvillages.stock.Stockpile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Growth: a village with a free bed and enough food in store gets a child every so often (the food is taken
 * from the warehouse). Children grow up in a Minecraft day's worth of ticks and join the work force.
 */
final class Births {
    private static final int FOOD_PER_CHILD = 12;
    private static final List<Item> FOOD = List.of(Items.BREAD, Items.CARROT, Items.POTATO, Items.BEETROOT,
            Items.BAKED_POTATO, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN,
            Items.COOKED_COD, Items.COOKED_SALMON, Items.APPLE, Items.SWEET_BERRIES,
            // Raw meat last: a camp with no oven yet lives on what the hunter brings.
            Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT);

    private Births() {
    }

    static void tick(ServerLevel level, Village v, Stockpile stock) {
        long now = level.getGameTime();
        long last = v.data().getLong("lastBirth");
        if (now - last < LVConfig.BIRTH_INTERVAL.get() || v.beds() <= v.population()) {
            return;
        }
        if (!eat(stock)) {
            return;
        }
        Villager child = EntityType.VILLAGER.create(level);
        if (child == null) {
            return;
        }
        BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                v.center().offset(level.random.nextInt(7) - 3, 0, level.random.nextInt(7) - 3));
        child.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        child.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.BREEDING, null, null);
        child.setAge(-24000);
        level.addFreshEntity(child);
        level.playSound(null, at, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        v.data().putLong("lastBirth", now);
        v.markDirty();
        LivingVillages.LOGGER.info("Village {}: a child was born", v.id().toString().substring(0, 8));
    }

    /** Takes a child's worth of food from the store: cooked food and vegetables, or wheat (three per unit). */
    private static boolean eat(Stockpile stock) {
        int units = 0;
        for (Item f : FOOD) {
            units += stock.count(f);
        }
        units += stock.count(Items.WHEAT) / 3;
        if (units < FOOD_PER_CHILD) {
            return false;
        }
        int left = FOOD_PER_CHILD;
        for (Item f : FOOD) {
            int n = Math.min(left, stock.count(f));
            if (n > 0) {
                stock.take(f, n);
                left -= n;
            }
        }
        if (left > 0) {
            stock.take(Items.WHEAT, left * 3);
        }
        return true;
    }
}
