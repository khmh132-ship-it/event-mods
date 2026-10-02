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
    private long lastLogTrip = Long.MIN_VALUE / 2;

    /** The wooden version of this job's tool, or null if the job has none. */
    private Item woodenTool() {
        Item basic = worker.job().basicTool();
        if (basic == null) {
            return null;
        }
        String path = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(basic).getPath();
        if (!path.startsWith("stone_") && !path.startsWith("wooden_")) {
            return null;
        }
        Item w = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation(path.replace("stone_", "wooden_")));
        return w == null || w == net.minecraft.world.item.Items.AIR ? null : w;
    }

    Tooling(VillageWorker worker) {
        this.worker = worker;
        this.fetching = new Fetching(worker);
    }

    void reset() {
        fetching.reset();
    }

    private long lastUpgrade = Long.MIN_VALUE / 2;

    /**
     * Now and then: a better tool in the stores is fetched (iron over stone, diamond over iron, enchanted over
     * plain); with diamonds or iron in store but no such tool, one is asked for.
     */
    private void upgrade(ServerLevel level, Village village, TagKey<Item> tag) {
        long now = level.getGameTime();
        if (now - lastUpgrade < 2400) {
            return;
        }
        lastUpgrade = now;
        swapToBest(tag);
        ItemStack hand = worker.getMainHandItem();
        int mine = tier(hand);
        var stock = village.stock(level).totals();
        Item better = null;
        for (Map.Entry<Item, Integer> e : stock.entrySet()) {
            if (e.getValue() > 0 && new ItemStack(e.getKey()).is(tag) && Gear.score(e.getKey()) > mine
                    && (better == null || Gear.score(e.getKey()) > Gear.score(better))) {
                better = e.getKey();
            }
        }
        if (better != null) {
            fetching.start(Map.of(better, 1));
            return;
        }
        for (String[] m : new String[][]{{"diamond_", "DIAMOND"}, {"iron_", "IRON_INGOT"}}) {
            Item target = Gear.inMaterial(hand.getItem(), m[0]);
            Item mat = m[1].equals("DIAMOND") ? net.minecraft.world.item.Items.DIAMOND : net.minecraft.world.item.Items.IRON_INGOT;
            if (target != null && Gear.score(target) > Gear.score(hand.getItem()) && stock.getOrDefault(mat, 0) >= 3) {
                village.request(target, 1, "tool:" + worker.getUUID(), now);
                return;
            }
        }
    }

    /** Puts the best tool of the kind he carries into his hand (the old one goes into his pocket). */
    private void swapToBest(TagKey<Item> tag) {
        SimpleContainer inv = worker.getInventory();
        int best = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(tag) && tier(inv.getItem(i)) > tier(worker.getMainHandItem())
                    && (best < 0 || tier(inv.getItem(i)) > tier(inv.getItem(best)))) {
                best = i;
            }
        }
        if (best >= 0) {
            ItemStack tool = inv.removeItemNoUpdate(best);
            ItemStack old = worker.getMainHandItem();
            worker.setItemInHand(InteractionHand.MAIN_HAND, tool);
            if (!old.isEmpty()) {
                inv.addItem(old);
            }
        }
    }

    Status tick(ServerLevel level, Village village) {
        TagKey<Item> tag = worker.job().toolTag();
        if (tag == null) {
            return Status.READY;
        }
        if (worker.getMainHandItem().is(tag)) {
            if (fetching.active()) {
                fetching.tick(); // going for a better one
                if (!fetching.active()) {
                    swapToBest(tag);
                }
                return Status.BUSY;
            }
            upgrade(level, village, tag);
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
        // Nobody to make one: two logs make planks and sticks, and those a wooden tool, as a player starts out.
        Item ownMake = woodenTool();
        if (ownMake != null) {
            SimpleContainer pocket = worker.getInventory();
            for (int i = 0; i < pocket.getContainerSize(); i++) {
                ItemStack s = pocket.getItem(i);
                if (s.is(net.minecraft.tags.ItemTags.LOGS) && s.getCount() >= 2) {
                    s.shrink(2);
                    pocket.setChanged();
                    worker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ownMake));
                    worker.swing(InteractionHand.MAIN_HAND);
                    return Status.READY;
                }
            }
            if (!fetching.active() && level.getGameTime() - lastLogTrip > 1200) {
                for (Map.Entry<Item, Integer> e : village.stock(level).totals().entrySet()) {
                    if (e.getValue() >= 2 && new ItemStack(e.getKey()).is(net.minecraft.tags.ItemTags.LOGS)) {
                        lastLogTrip = level.getGameTime();
                        fetching.start(Map.of(e.getKey(), 2));
                        return Status.BUSY;
                    }
                }
            }
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
        return Gear.score(s);
    }
}
