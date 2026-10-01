package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.village.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * A worker's tool. He works with it in hand; it wears out with every block like a player's and breaks in the
 * end. Without one he takes the best one from his pockets or the warehouse, and if there is none he asks for
 * one (the apprentice makes it) and meanwhile works with bare hands, which is slow and gets nothing from stone.
 */
class Tooling {
    enum Status { READY, BUSY, BARE }

    private final VillageWorker worker;
    private final Fetching fetching;
    private long lastRequest = Long.MIN_VALUE / 2;

    Tooling(VillageWorker worker) {
        this.worker = worker;
        this.fetching = new Fetching(worker);
    }

    void reset() {
        fetching.reset();
    }

    Status tick(ServerLevel level, Village village) {
        TagKey<Item> tag = worker.job().toolTag();
        if (tag == null || worker.getMainHandItem().is(tag)) {
            return Status.READY;
        }
        SimpleContainer inv = worker.getInventory();
        int best = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(tag) && (best < 0 || tier(inv.getItem(i)) > tier(inv.getItem(best)))) {
                best = i;
            }
        }
        if (best >= 0) {
            ItemStack tool = inv.removeItemNoUpdate(best);
            ItemStack old = worker.getMainHandItem();
            worker.setItemInHand(InteractionHand.MAIN_HAND, tool);
            if (!old.isEmpty()) {
                worker.carry(old.getItem(), old.getCount());
            }
            return Status.READY;
        }
        if (fetching.active()) {
            fetching.tick();
            return Status.BUSY;
        }
        Item inStock = null;
        for (Map.Entry<Item, Integer> e : village.stock(level).totals().entrySet()) {
            if (e.getValue() > 0 && new ItemStack(e.getKey()).is(tag)
                    && (inStock == null || tier(new ItemStack(e.getKey())) > tier(new ItemStack(inStock)))) {
                inStock = e.getKey();
            }
        }
        if (inStock != null) {
            fetching.start(Map.of(inStock, 1));
            return Status.BUSY;
        }
        long now = level.getGameTime();
        if (now - lastRequest > 600) {
            Item want = worker.job().basicTool();
            if (village.stock(level).count(net.minecraft.world.item.Items.COBBLESTONE) < 3) {
                // No stone yet: a wooden one will do for now.
                Item wooden = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(
                        net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(want).getPath().replace("stone_", "wooden_")));
                if (wooden != null && wooden != net.minecraft.world.item.Items.AIR) {
                    want = wooden;
                }
            }
            village.request(want, 1, "tool:" + worker.getUUID(), now);
            lastRequest = now;
        }
        return Status.BARE;
    }

    /** Ticks a player would need to break the block with what is in hand. */
    int breakTicks(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack hand = worker.getMainHandItem();
        float hardness = state.getDestroySpeed(level, pos);
        float speed = Math.max(1.0F, hand.getDestroySpeed(state));
        boolean proper = !state.requiresCorrectToolForDrops() || hand.isCorrectToolForDrops(state);
        return Math.max(4, (int) Math.round(hardness * (proper ? 30 : 100) / speed / worker.workSpeed()));
    }

    boolean canHarvest(BlockState state) {
        return !state.requiresCorrectToolForDrops() || worker.getMainHandItem().isCorrectToolForDrops(state);
    }

    /** Wears the tool down by one use; a broken tool simply disappears from the hand. */
    void used() {
        ItemStack hand = worker.getMainHandItem();
        if (!hand.isEmpty() && hand.isDamageableItem()) {
            hand.hurtAndBreak(1, worker, w -> w.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
    }

    private static int tier(ItemStack s) {
        return s.getItem() instanceof TieredItem t ? t.getTier().getLevel() * 100 + (int) t.getTier().getSpeed() : 0;
    }
}
