package com.khmh.livingvillages.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.List;

/**
 * Using a furnace (or smoker, blast furnace) the way a player does: take out what is finished, put in what
 * is to be cooked, feed it fuel from the pockets. Nobody waits by it; the result is picked up next time.
 */
final class FurnaceUse {
    private static final List<Item> FUELS = List.of(Items.COAL, Items.CHARCOAL);

    private FurnaceUse() {
    }

    /** The worker stands at the furnace: collect, load the first of {@code inputs} he carries, add fuel. */
    static void serve(VillageWorker worker, ServerLevel level, BlockPos pos, List<Item> inputs) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
            return;
        }
        SimpleContainer inv = worker.getInventory();
        ItemStack out = furnace.getItem(2);
        if (!out.isEmpty()) {
            worker.carry(out.getItem(), out.getCount());
            furnace.setItem(2, ItemStack.EMPTY);
        }
        ItemStack in = furnace.getItem(0);
        for (Item item : inputs) {
            if (inv.countItem(item) == 0 || !in.isEmpty() && !in.is(item)) {
                continue;
            }
            int n = Math.min(inv.countItem(item), 64 - in.getCount());
            inv.removeItemType(item, n);
            furnace.setItem(0, new ItemStack(item, in.getCount() + n));
            ItemStack fuel = furnace.getItem(1);
            for (Item f : FUELS) {
                int need = Math.min(inv.countItem(f), (n + 7) / 8);
                if (need > 0 && (fuel.isEmpty() || fuel.is(f)) && fuel.getCount() + need <= 64) {
                    inv.removeItemType(f, need);
                    furnace.setItem(1, new ItemStack(f, fuel.getCount() + need));
                    break;
                }
            }
            break;
        }
        furnace.setChanged();
    }
}
