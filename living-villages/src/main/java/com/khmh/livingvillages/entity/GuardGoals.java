package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * The village guard: armed and armoured from the warehouse (asking for a sword and armour when there are none),
 * walks a round between the bell and the buildings, attacks monsters inside the village (and anything that hits
 * him), and falls back to the bell to recover when badly hurt.
 */
final class GuardGoals {
    private GuardGoals() {
    }

    static boolean isGuard(VillageWorker w) {
        return w.job() == WorkerJob.GUARD;
    }

    /** Sword fighting, only while healthy enough. */
    static class Melee extends MeleeAttackGoal {
        private final VillageWorker worker;

        Melee(VillageWorker worker) {
            super(worker, 0.75, true);
            this.worker = worker;
        }

        @Override
        public boolean canUse() {
            return isGuard(worker) && worker.getHealth() > worker.getMaxHealth() * 0.35 && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return isGuard(worker) && worker.getHealth() > worker.getMaxHealth() * 0.35 && super.canContinueToUse();
        }

        @Override
        protected void checkAndPerformAttack(net.minecraft.world.entity.LivingEntity enemy, double distSqr) {
            if (distSqr <= getAttackReachSqr(enemy) && isTimeToAttack()) {
                resetAttackCooldown();
                worker.swing(InteractionHand.MAIN_HAND);
                worker.doHurtTarget(enemy);
                ItemStack weapon = worker.getMainHandItem();
                if (weapon.isDamageableItem()) {
                    weapon.hurtAndBreak(1, worker, w -> w.broadcastBreakEvent(EquipmentSlot.MAINHAND));
                }
            }
        }
    }

    /** Monsters inside the village, creepers only when they get close to the guard. */
    static class Hunt extends NearestAttackableTargetGoal<Monster> {
        private final VillageWorker worker;

        Hunt(VillageWorker worker) {
            super(worker, Monster.class, 10, true, false, m -> !(m instanceof Creeper) || m.distanceToSqr(worker) < 25);
            this.worker = worker;
        }

        @Override
        public boolean canUse() {
            if (!isGuard(worker) || !super.canUse()) {
                return false;
            }
            Village v = worker.village().orElse(null);
            return v != null && target != null && v.center().distSqr(target.blockPosition())
                    <= (double) (v.radius() + 16) * (v.radius() + 16);
        }
    }

    static class Retaliate extends HurtByTargetGoal {
        private final VillageWorker worker;

        Retaliate(VillageWorker worker) {
            super(worker);
            this.worker = worker;
        }

        @Override
        public boolean canUse() {
            return isGuard(worker) && worker.getLastHurtByMob() instanceof Enemy && super.canUse();
        }
    }

    /** Gearing up, walking the rounds, resting at the bell when hurt. */
    static class Patrol extends Goal {
        private static final Map<EquipmentSlot, List<Item>> ARMOR = Map.of(
                EquipmentSlot.HEAD, List.of(Items.IRON_HELMET, Items.CHAINMAIL_HELMET, Items.LEATHER_HELMET),
                EquipmentSlot.CHEST, List.of(Items.IRON_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.LEATHER_CHESTPLATE),
                EquipmentSlot.LEGS, List.of(Items.IRON_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.LEATHER_LEGGINGS),
                EquipmentSlot.FEET, List.of(Items.IRON_BOOTS, Items.CHAINMAIL_BOOTS, Items.LEATHER_BOOTS));

        private final VillageWorker worker;
        private final Tooling sword;
        private final Fetching fetching;
        private final List<BlockPos> round = new ArrayList<>();
        private int stop;
        private int timer;
        private long lastRequest = Long.MIN_VALUE / 2;

        Patrol(VillageWorker worker) {
            this.worker = worker;
            this.sword = new Tooling(worker);
            this.fetching = new Fetching(worker);
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return isGuard(worker) && worker.getTarget() == null && worker.village().isPresent();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            fetching.reset();
            sword.reset();
        }

        @Override
        public void tick() {
            Village village = worker.village().orElse(null);
            if (village == null || !(worker.level() instanceof ServerLevel level)) {
                return;
            }
            timer++;
            if (fetching.active()) {
                worker.setStatus("fetching armour");
                fetching.tick();
                return;
            }
            if (sword.tick(level, village) == Tooling.Status.BUSY) {
                worker.setStatus("fetching a weapon");
                return;
            }
            if (gearUp(level, village)) {
                return;
            }
            if (worker.getHealth() < worker.getMaxHealth() * 0.5) {
                // Hurt: back to the bell to recover.
                worker.setStatus("recovering");
                BlockPos bell = village.center();
                if (worker.distanceToSqr(bell.getX() + 0.5, bell.getY(), bell.getZ() + 0.5) > 16) {
                    if (timer % 20 == 1) {
                        worker.getNavigation().moveTo(bell.getX() + 0.5, bell.getY(), bell.getZ() + 0.5, 0.7);
                    }
                } else if (timer % 40 == 0) {
                    worker.heal(1.0F);
                }
                return;
            }
            if (round.isEmpty() || timer % 2400 == 0) {
                planRound(village);
            }
            BlockPos p = round.get(stop % round.size());
            worker.setStatus("patrolling, stop " + (stop % round.size() + 1) + "/" + round.size());
            if (worker.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 9 || timer % 600 == 0) {
                stop++;
            } else if (timer % 40 == 1) {
                worker.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.55);
            }
        }

        /** Takes armour he carries or the warehouse has; asks for a set when there is none. True while busy. */
        private boolean gearUp(ServerLevel level, Village village) {
            var stock = village.stock(level).totals();
            for (Map.Entry<EquipmentSlot, List<Item>> slot : ARMOR.entrySet()) {
                if (!worker.getItemBySlot(slot.getKey()).isEmpty()) {
                    continue;
                }
                for (Item piece : slot.getValue()) {
                    if (worker.getInventory().countItem(piece) > 0) {
                        worker.getInventory().removeItemType(piece, 1);
                        worker.setItemSlot(slot.getKey(), new ItemStack(piece));
                        worker.setDropChance(slot.getKey(), 1.0F);
                        return true;
                    }
                }
                for (Item piece : slot.getValue()) {
                    if (stock.getOrDefault(piece, 0) > 0) {
                        fetching.start(Map.of(piece, 1));
                        return true;
                    }
                }
                long now = level.getGameTime();
                if (now - lastRequest > 2400 && slot.getKey() == EquipmentSlot.CHEST) {
                    Item want = stock.getOrDefault(Items.IRON_INGOT, 0) >= 8 ? Items.IRON_CHESTPLATE : Items.LEATHER_CHESTPLATE;
                    village.request(want, 1, "guard:" + worker.getUUID(), now);
                    lastRequest = now;
                }
            }
            return false;
        }

        private void planRound(Village village) {
            round.clear();
            round.add(village.center());
            village.buildings().stream().filter(b -> b.isComplete()).map(b -> b.entrance()).forEach(round::add);
            int r = village.radius() - 8;
            round.add(village.center().offset(r, 0, 0));
            round.add(village.center().offset(0, 0, r));
            round.add(village.center().offset(-r, 0, 0));
            round.add(village.center().offset(0, 0, -r));
            round.replaceAll(p -> VillageWorker.standAt(worker.level(), p));
        }
    }
}
