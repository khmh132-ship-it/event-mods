package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Shepherd and butcher. Both keep the village's pens stocked: they find animals roaming around, lead them to a
 * pen and lift them over the fence, and feed pairs in the pens so they breed. The shepherd shears every sheep
 * with wool on it; the butcher slaughters animals outside the pens and the surplus inside (keeping a breeding
 * stock), and smokes the meat in a smoker. Everything dropped is picked up and taken to the warehouse.
 */
public class AnimalWorkGoal extends Goal {
    private static final int RANGE = 32;
    private static final int KEEP_IN_PEN = 4;
    private static final int LOAD = 32;
    /** A player's reach: enough to shear or feed an animal over a fence. */
    private static final double REACH = 4.0;
    private static final List<Item> RAW_MEAT = List.of(Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON,
            Items.RABBIT);
    private static final Map<EntityType<?>, Item> FEED = Map.of(EntityType.SHEEP, Items.WHEAT,
            EntityType.COW, Items.WHEAT, EntityType.PIG, Items.CARROT, EntityType.CHICKEN, Items.WHEAT_SEEDS);

    private enum Task { NONE, SHEAR, LEAD, BREED, CULL, COOK, RETURN }

    private final VillageWorker worker;
    private final Tooling tooling;
    private final Fetching fetching;
    private Task task = Task.NONE;
    private Animal animal;
    private BlockPos spot;
    private int timer;
    private int think;
    private long lastBreed = Long.MIN_VALUE / 2;

    public AnimalWorkGoal(VillageWorker worker) {
        this.worker = worker;
        this.tooling = new Tooling(worker);
        this.fetching = new Fetching(worker);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean shepherd() {
        return worker.job() == WorkerJob.SHEPHERD;
    }

    @Override
    public boolean canUse() {
        return (worker.job() == WorkerJob.SHEPHERD || worker.job() == WorkerJob.BUTCHER) && worker.village().isPresent();
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
        task = Task.NONE;
        animal = null;
        tooling.reset();
        fetching.reset();
        worker.resetUnloading();
        worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        Village village = worker.village().orElse(null);
        if (village == null || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        timer++;
        if (fetching.active()) {
            fetching.tick();
            return;
        }
        if (tooling.tick(level, village) == Tooling.Status.BUSY) {
            return;
        }
        if (task == Task.NONE && --think <= 0) {
            think = 20;
            decide(level, village);
            timer = 0;
        }
        worker.setStatus(task.name().toLowerCase() + (animal != null && task != Task.RETURN && task != Task.COOK
                ? " " + animal.getType().toShortString() + "@" + animal.blockPosition().toShortString() : "") + " t" + timer);
        switch (task) {
            case SHEAR -> shear(level);
            case LEAD -> lead(level, village);
            case BREED -> breed(level);
            case CULL -> cull(level);
            case COOK -> cook(level);
            case RETURN -> {
                if (worker.unloadTick()) {
                    task = Task.NONE;
                }
            }
            default -> { }
        }
    }

    private void decide(ServerLevel level, Village village) {
        List<BoundingBox> pens = pens(village);
        List<Animal> around = level.getEntitiesOfClass(Animal.class, new AABB(worker.blockPosition()).inflate(RANGE),
                a -> a.isAlive() && (a instanceof Sheep || a instanceof Cow || a instanceof Pig
                        || a instanceof Chicken || a instanceof Rabbit));
        if (worker.carried() >= LOAD || worker.inventoryFull()) {
            task = !shepherd() && RAW_MEAT.stream().anyMatch(m -> worker.getInventory().countItem(m) > 0)
                    && smoker(level, village) != null ? Task.COOK : Task.RETURN;
            return;
        }
        if (shepherd()) {
            animal = nearest(around.stream().filter(a -> a instanceof Sheep s && s.readyForShearing()).toList());
            if (animal != null) {
                task = Task.SHEAR;
                return;
            }
        } else {
            // Wild animals, and whatever the pens hold beyond a breeding stock.
            // Sheep are the shepherd's while the flock is small.
            boolean spareSheep = village.professions().getOrDefault("shepherd", 0) == 0
                    || around.stream().filter(a -> a instanceof Sheep).count() >= 8;
            animal = nearest(around.stream().filter(a -> !a.isBaby() && (spareSheep || !(a instanceof Sheep))
                    && (!inPen(pens, a) || countInPens(pens, around, a.getType()) > KEEP_IN_PEN)).toList());
            if (animal != null) {
                // Bring a breeding pair in first when a pen is short of this kind.
                if (!pens.isEmpty() && !inPen(pens, animal) && countInPens(pens, around, animal.getType()) < 2) {
                    task = Task.LEAD;
                    return;
                }
                task = Task.CULL;
                return;
            }
        }
        if (!pens.isEmpty()) {
            EntityType<?> kind = shepherd() ? EntityType.SHEEP : null;
            Animal stray = nearest(around.stream().filter(a -> !a.isBaby() && !inPen(pens, a)
                    && (kind == null || a.getType() == kind)
                    && countInPens(pens, around, a.getType()) < KEEP_IN_PEN).toList());
            if (stray != null) {
                animal = stray;
                task = Task.LEAD;
                return;
            }
            if (level.getGameTime() - lastBreed > 6000) {
                task = Task.BREED;
                return;
            }
        }
        if (worker.carried() > 0) {
            task = !shepherd() && RAW_MEAT.stream().anyMatch(m -> worker.getInventory().countItem(m) > 0)
                    && smoker(level, village) != null ? Task.COOK : Task.RETURN;
        }
    }

    private void shear(ServerLevel level) {
        if (!(animal instanceof Sheep sheep) || !sheep.isAlive() || !sheep.readyForShearing() || timer > 300) {
            task = Task.NONE;
            return;
        }
        if (approach(sheep.blockPosition(), REACH)) { // over the fence if need be
            sheep.shear(SoundSource.NEUTRAL); // wool drops and is picked up
            tooling.used();
            worker.swing(InteractionHand.MAIN_HAND);
            task = Task.NONE;
        }
    }

    /** Walks to the animal, then towards the nearest pen with it following, and lifts it over the fence. */
    private void lead(ServerLevel level, Village village) {
        List<BoundingBox> pens = pens(village);
        if (animal == null || !animal.isAlive() || pens.isEmpty() || timer > 1200) {
            task = Task.NONE;
            return;
        }
        BoundingBox pen = pens.stream().min(Comparator.comparingDouble(b -> b.getCenter().distSqr(animal.blockPosition())))
                .orElseThrow();
        if (pen.isInside(animal.blockPosition())) {
            task = Task.NONE;
            return;
        }
        if (worker.distanceToSqr(animal) > 16) {
            if (timer % 20 == 1) {
                worker.getNavigation().moveTo(animal, 0.7);
            }
            return;
        }
        // Close enough: the animal follows him to the pen.
        BlockPos gate = edgeNear(pen, animal.blockPosition());
        if (timer % 10 == 0) {
            worker.getNavigation().moveTo(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5, 0.6);
            animal.getNavigation().moveTo(worker, 1.1);
        }
        if (animal.distanceToSqr(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5) < 9) {
            BlockPos inside = new BlockPos(pen.getCenter().getX(), animal.getBlockY(), pen.getCenter().getZ());
            animal.teleportTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5); // lifted over the fence
            animal.getNavigation().stop();
            level.playSound(null, inside, SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5F, 0.8F);
            task = Task.NONE;
        }
    }

    /** Feeds two adults of a kind the pens have at least two of, if the feed is to hand. */
    private void breed(ServerLevel level) {
        Village village = worker.village().orElse(null);
        List<BoundingBox> pens = village == null ? List.of() : pens(village);
        for (BoundingBox pen : pens) {
            List<Animal> inPen = level.getEntitiesOfClass(Animal.class, AABB.of(pen),
                    a -> a.isAlive() && !a.isBaby() && a.canFallInLove() && FEED.containsKey(a.getType()));
            for (Animal a : inPen) {
                Item feed = FEED.get(a.getType());
                List<Animal> pair = inPen.stream().filter(b -> b.getType() == a.getType()).limit(2).toList();
                if (pair.size() < 2) {
                    continue;
                }
                if (worker.getInventory().countItem(feed) < 2) {
                    if (village.stock(level).count(feed) >= 2) {
                        fetching.start(Map.of(feed, 8));
                    } else {
                        lastBreed = level.getGameTime(); // nothing to feed them with; try later
                        task = Task.NONE;
                    }
                    return;
                }
                if (!approach(a.blockPosition(), REACH)) {
                    if (timer > 300) {
                        lastBreed = level.getGameTime();
                        task = Task.NONE;
                    }
                    return;
                }
                for (Animal p : pair) {
                    p.setInLove(null);
                }
                worker.getInventory().removeItemType(feed, 2);
                worker.swing(InteractionHand.MAIN_HAND);
                lastBreed = level.getGameTime();
                task = Task.NONE;
                return;
            }
        }
        lastBreed = level.getGameTime();
        task = Task.NONE;
    }

    private void cull(ServerLevel level) {
        if (animal == null || !animal.isAlive() || timer > 600) {
            task = Task.NONE;
            return;
        }
        if (approach(animal.blockPosition(), 2.2) && timer % 15 == 0) {
            float damage = 2.0F + (worker.getMainHandItem().isEmpty() ? 0 : 3.0F);
            animal.hurt(level.damageSources().mobAttack(worker), damage);
            worker.swing(InteractionHand.MAIN_HAND);
            tooling.used();
        }
    }

    /** Raw meat goes into the smoker on the way to the warehouse; smoked meat is picked up next time. */
    private void cook(ServerLevel level) {
        Village village = worker.village().orElse(null);
        BlockPos at = village == null ? null : smoker(level, village);
        if (at == null || timer > 600) {
            task = Task.RETURN;
            return;
        }
        if (approach(at, 2.5)) {
            FurnaceUse.serve(worker, level, at, RAW_MEAT);
            task = Task.RETURN;
        }
    }

    @Nullable
    private BlockPos smoker(ServerLevel level, Village village) {
        BlockPos best = null;
        for (Building b : village.buildings()) {
            if (!b.isComplete()) {
                continue;
            }
            BoundingBox box = b.box();
            for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (level.getBlockEntity(p) instanceof SmokerBlockEntity) {
                    return p.immutable();
                }
                if (best == null && level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity) {
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    private boolean approach(BlockPos target, double reach) {
        if (worker.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= reach * reach) {
            worker.getNavigation().stop();
            worker.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            return true;
        }
        if (timer % 15 == 1) {
            worker.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.65);
        }
        return false;
    }

    /** Pens: the fenced areas of animal pens, stables and the shepherd's and butcher's houses. */
    private static List<BoundingBox> pens(Village village) {
        return village.buildings().stream().filter(Building::isComplete)
                .filter(b -> b.type() != null && (b.type().group().equals("pen") || b.type().group().equals("stable")
                        || b.typeId().startsWith("shepherds_house") || b.typeId().startsWith("butcher_shop")))
                .map(Building::box).toList();
    }

    private static boolean inPen(List<BoundingBox> pens, Animal a) {
        return pens.stream().anyMatch(p -> p.isInside(a.blockPosition()));
    }

    private static long countInPens(List<BoundingBox> pens, List<Animal> around, EntityType<?> type) {
        return around.stream().filter(a -> a.getType() == type && inPen(pens, a)).count();
    }

    private static BlockPos edgeNear(BoundingBox pen, BlockPos from) {
        int x = Math.max(pen.minX() - 1, Math.min(pen.maxX() + 1, from.getX()));
        int z = Math.max(pen.minZ() - 1, Math.min(pen.maxZ() + 1, from.getZ()));
        return new BlockPos(x, from.getY(), z);
    }

    @Nullable
    private Animal nearest(List<Animal> list) {
        return list.stream().min(Comparator.comparingDouble(worker::distanceToSqr)).orElse(null);
    }
}
