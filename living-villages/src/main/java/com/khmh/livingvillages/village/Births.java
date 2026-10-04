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
    private static final int FOOD_PER_CHILD = 8; // a loaf or so a day for a few days: two dozen wheat
    private static final List<Item> FOOD = List.of(Items.BREAD, Items.CARROT, Items.POTATO, Items.BEETROOT,
            Items.BAKED_POTATO, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN,
            Items.COOKED_COD, Items.COOKED_SALMON, Items.APPLE, Items.SWEET_BERRIES,
            // Raw meat last: a camp with no oven yet lives on what the hunter brings.
            Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT);

    private Births() {
    }

    static void tick(ServerLevel level, Village v, Stockpile stock) {
        long now = level.getGameTime();
        CompoundTagHolder court = new CompoundTagHolder(v);
        if (court.active()) {
            courting(level, v, stock, court, now);
            return;
        }
        long last = v.data().getLong("lastBirth");
        if (now - last < LVConfig.BIRTH_INTERVAL.get() || v.beds() <= v.population() || !canFeed(stock)
                || com.khmh.livingvillages.entity.SleepGoal.isNight(level)) {
            return;
        }
        // Two grown-ups of the village who are about and not asleep: they go to each other.
        List<com.khmh.livingvillages.entity.VillageWorker> adults = Workers.of(level, v).stream()
                .filter(w -> w.isAlive() && !w.isSleeping() && w.getY() > v.center().getY() - 10).toList();
        if (adults.size() < 2) {
            if (Workers.of(level, v).isEmpty() && v.population() >= 2) {
                spawnChild(level, v, stock, v.center(), now); // an old village of plain villagers only: as before
            }
            return; // otherwise wait until two of them are about
        }
        com.khmh.livingvillages.entity.VillageWorker a = null, b = null;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < adults.size(); i++) {
            for (int j = i + 1; j < adults.size(); j++) {
                double d = adults.get(i).distanceToSqr(adults.get(j));
                if (d < best && d < 24 * 24) { // two who are about together, not one at each end of the woods
                    best = d;
                    a = adults.get(i);
                    b = adults.get(j);
                }
            }
        }
        if (a == null) {
            return; // nobody near anybody just now: another time
        }
        a.courting(b, now + 2400);
        b.courting(a, now + 2400);
        court.start(a, b, now);
    }

    /** The pair walk up to each other; once together, hearts, and a child is born between them. */
    private static void courting(ServerLevel level, Village v, Stockpile stock, CompoundTagHolder court, long now) {
        var a = level.getEntity(court.a()) instanceof com.khmh.livingvillages.entity.VillageWorker w ? w : null;
        var b = level.getEntity(court.b()) instanceof com.khmh.livingvillages.entity.VillageWorker w ? w : null;
        if (a == null || b == null || !a.isAlive() || !b.isAlive() || now - court.since() > 2400) {
            if (a != null) a.courting(null, 0);
            if (b != null) b.courting(null, 0);
            court.clear();
            return;
        }
        if (a.distanceToSqr(b) > 9) {
            court.apart();
            return;
        }
        if (!court.together()) {
            court.meet(now); // hearts for a few seconds first
            return;
        }
        if (now - court.metAt() >= 60) {
            BlockPos at = BlockPos.containing(a.position().add(b.position()).scale(0.5));
            if (canFeed(stock)) {
                spawnChild(level, v, stock, at, now);
            }
            a.courting(null, 0);
            b.courting(null, 0);
            court.clear();
        }
    }

    private static void spawnChild(ServerLevel level, Village v, Stockpile stock, BlockPos near, long now) {
        if (!eat(stock)) {
            return;
        }
        Villager child = EntityType.VILLAGER.create(level);
        if (child == null) {
            return;
        }
        BlockPos at = near.equals(v.center()) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                v.center().offset(level.random.nextInt(7) - 3, 0, level.random.nextInt(7) - 3)) : near;
        child.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        child.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.BREEDING, null, null);
        child.setAge(-24000);
        level.addFreshEntity(child);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, at.getX() + 0.5, at.getY() + 1.0,
                at.getZ() + 0.5, 6, 0.5, 0.4, 0.5, 0.1);
        level.playSound(null, at, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        v.data().putLong("lastBirth", now);
        v.markDirty();
        LivingVillages.LOGGER.info("Village {}: a child was born", v.id().toString().substring(0, 8));
    }

    /** The courting pair, kept in the village's data so it survives a save. */
    private record CompoundTagHolder(Village v) {
        boolean active() {
            return v.data().hasUUID("mateA");
        }

        java.util.UUID a() {
            return v.data().getUUID("mateA");
        }

        java.util.UUID b() {
            return v.data().getUUID("mateB");
        }

        long since() {
            return v.data().getLong("courtSince");
        }

        boolean together() {
            return v.data().contains("courtMet");
        }

        long metAt() {
            return v.data().getLong("courtMet");
        }

        void start(net.minecraft.world.entity.Entity a, net.minecraft.world.entity.Entity b, long now) {
            v.data().putUUID("mateA", a.getUUID());
            v.data().putUUID("mateB", b.getUUID());
            v.data().putLong("courtSince", now);
            v.data().remove("courtMet");
            v.markDirty();
        }

        void meet(long now) {
            v.data().putLong("courtMet", now);
        }

        void apart() {
            v.data().remove("courtMet");
        }

        void clear() {
            v.data().remove("mateA");
            v.data().remove("mateB");
            v.data().remove("courtSince");
            v.data().remove("courtMet");
            v.markDirty();
        }
    }

    /** Whether the store holds a child's worth of food. */
    private static boolean canFeed(Stockpile stock) {
        int units = 0;
        for (Item f : FOOD) {
            units += stock.count(f);
        }
        return units + stock.count(Items.WHEAT) / 3 >= FOOD_PER_CHILD;
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
