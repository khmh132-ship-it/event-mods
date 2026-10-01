package com.khmh.livingvillages.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** Villagers pick up whatever lies around (dropped loot, a fallen worker's belongings), like a player would. */
public class CollectItemsGoal extends Goal {
    private static final double RANGE = 10.0;

    private final VillageWorker worker;
    private ItemEntity target;
    private int timer;
    private int cooldown;

    public CollectItemsGoal(VillageWorker worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0 || worker.inventoryFull()) {
            return false;
        }
        cooldown = 10;
        List<ItemEntity> items = worker.level().getEntitiesOfClass(ItemEntity.class,
                worker.getBoundingBox().inflate(RANGE, 4, RANGE),
                e -> e.isAlive() && !e.hasPickUpDelay() && worker.wantsToPickUp(e.getItem()));
        target = items.stream().min(Comparator.comparingDouble(worker::distanceToSqr)).orElse(null);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && timer < 200 && !worker.inventoryFull();
    }

    @Override
    public void start() {
        timer = 0;
    }

    @Override
    public void stop() {
        target = null;
        worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (worker.distanceToSqr(target) < 4.0) {
            worker.pickUpItem(target); // within arm's reach, e.g. lying on a table
            return;
        }
        if (++timer % 10 == 1) {
            worker.getNavigation().moveTo(target, 0.7);
        }
    }
}
