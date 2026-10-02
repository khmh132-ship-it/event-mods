package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.village.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Map;

/**
 * Hungry workers eat: from their pockets if they carry food, otherwise they fetch some from the warehouse. With
 * only raw wheat in store they ask for bread. Starving workers do nothing else until they have eaten.
 */
public class EatGoal extends Goal {
    private final VillageWorker worker;
    private final Fetching fetching;
    private int cooldown;
    private long lastRequest = Long.MIN_VALUE / 2;

    public EatGoal(VillageWorker worker) {
        this.worker = worker;
        this.fetching = new Fetching(worker);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0 || !worker.isHungry() || worker.village().isEmpty()) {
            return false;
        }
        cooldown = 40;
        if (foodSlot() < 0 && worker.getInventory().countItem(Items.WHEAT) < 3 && !hasFoodInStock()) {
            askForBread();
            return false; // nothing to eat anywhere: better get on with work (an apprentice may bake it)
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return worker.isHungry() && (fetching.active() || foodSlot() >= 0
                || worker.getInventory().countItem(Items.WHEAT) >= 3);
    }

    @Override
    public void start() {
        fetching.reset();
    }

    @Override
    public void stop() {
        fetching.reset();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Three wheat make a loaf: with nobody to bake, a hungry villager does it himself. */
    private boolean bake() {
        SimpleContainer inv = worker.getInventory();
        if (inv.countItem(Items.WHEAT) < 3) {
            return false;
        }
        inv.removeItemType(Items.WHEAT, 3);
        worker.carry(Items.BREAD, 1);
        worker.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    @Override
    public void tick() {
        if (foodSlot() < 0) {
            bake();
        }
        int slot = foodSlot();
        if (slot >= 0) {
            eat(slot);
            return;
        }
        if (fetching.active()) {
            fetching.tick();
            return;
        }
        Village village = worker.village().orElse(null);
        if (village == null || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        for (Map.Entry<Item, Integer> e : village.stock(level).totals().entrySet()) {
            if (e.getValue() > 0 && e.getKey().getFoodProperties(new ItemStack(e.getKey()), worker) != null) {
                fetching.start(Map.of(e.getKey(), Math.min(4, e.getValue())));
                return;
            }
        }
        if (village.stock(level).count(Items.WHEAT) >= 3) {
            fetching.start(Map.of(Items.WHEAT, Math.min(9, village.stock(level).count(Items.WHEAT))));
            return;
        }
        long now = level.getGameTime();
        if (now - lastRequest > 1200) {
            village.request(Items.BREAD, 4, "food", now); // the apprentice bakes it from wheat
            lastRequest = now;
        }
    }

    private void eat(int slot) {
        SimpleContainer inv = worker.getInventory();
        ItemStack stack = inv.getItem(slot);
        FoodProperties food = stack.getItem().getFoodProperties(stack, worker);
        worker.swing(InteractionHand.MAIN_HAND);
        worker.level().playSound(null, worker.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, 1.0F);
        worker.feed(food == null ? 2 : food.getNutrition());
        stack.shrink(1);
        inv.setChanged();
    }

    private int foodSlot() {
        SimpleContainer inv = worker.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem().getFoodProperties(s, worker) != null && !s.is(Items.ROTTEN_FLESH)
                    && !s.is(Items.SPIDER_EYE) && !s.is(Items.POISONOUS_POTATO)) {
                return i;
            }
        }
        return -1;
    }

    private void askForBread() {
        Village village = worker.village().orElse(null);
        if (village != null && worker.level() instanceof ServerLevel level && level.getGameTime() - lastRequest > 1200) {
            village.request(Items.BREAD, 4, "food", level.getGameTime());
            lastRequest = level.getGameTime();
        }
    }

    private boolean hasFoodInStock() {
        return worker.village().map(v -> worker.level() instanceof ServerLevel l && v.stock(l).totals().entrySet()
                .stream().anyMatch(e -> e.getValue() > 0 && e.getKey().isEdible()
                || e.getKey() == Items.WHEAT && e.getValue() >= 3)).orElse(false);
    }
}
