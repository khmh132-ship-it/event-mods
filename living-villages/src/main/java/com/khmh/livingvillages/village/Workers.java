package com.khmh.livingvillages.village;

import com.khmh.livingvillages.LivingVillages;
import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.building.CostKey;
import com.khmh.livingvillages.entity.LVEntities;
import com.khmh.livingvillages.entity.VillageWorker;
import com.khmh.livingvillages.entity.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/** Hiring: a finished workplace without a worker takes on an unemployed adult villager. */
public final class Workers {
    private Workers() {
    }

    public static List<VillageWorker> of(ServerLevel level, Village v) {
        AABB box = new AABB(v.center()).inflate(v.radius() + 128); // miners tunnel far out
        return level.getEntitiesOfClass(VillageWorker.class, box,
                w -> w.isAlive() && v.id().equals(w.villageId()));
    }

    static void hire(ServerLevel level, Village v, List<Villager> villagers, List<VillageWorker> workers) {
        // A village that builds always has a builder, one that needs things made an apprentice, workplace or not.
        boolean building = v.buildings().stream().anyMatch(b -> !b.isComplete());
        boolean orders = v.requests().stream().anyMatch(r -> r.remaining() > 0);
        // Wood is the first thing every village needs: a lumberjack works from the bell until he has a hut.
        boolean woodShort = CostKey.Wood.INSTANCE.available(v.stock(level)) < 512
                || v.buildings().stream().anyMatch(b -> "lumberjack_hut".equals(b.typeId()) && b.isComplete());
        // Likewise stone: a miner digs his own quarry until there is a mine.
        boolean stoneShort = CostKey.Stone.INSTANCE.available(v.stock(level)) < 128
                || v.buildings().stream().anyMatch(b -> "mine".equals(b.typeId()) && b.isComplete());
        if (!villageWide(level, v, villagers, workers, WorkerJob.BUILDER, building)
                || !villageWide(level, v, villagers, workers, WorkerJob.APPRENTICE, orders)
                || !villageWide(level, v, villagers, workers, WorkerJob.LUMBERJACK, woodShort)
                || !villageWide(level, v, villagers, workers, WorkerJob.MINER, stoneShort)) {
            return;
        }

        for (Building b : v.buildings()) {
            WorkerJob job = WorkerJob.forWorkplace(b.typeId());
            if (job == null || !b.isComplete() || b.workerId() != null) {
                continue;
            }
            VillageWorker worker = convert(level, v, villagers, job, b, b.entrance());
            if (worker == null) {
                return; // nobody free; try again on the next scan
            }
            b.setWorkerId(worker.getUUID());
        }
    }

    /** Hires (if needed) and houses a village-wide worker; false if someone was just hired this scan. */
    private static boolean villageWide(ServerLevel level, Village v, List<Villager> villagers,
                                       List<VillageWorker> workers, WorkerJob job, boolean needed) {
        VillageWorker worker = workers.stream().filter(w -> w.job() == job).findFirst().orElse(null);
        // Known by id: he may simply be out of sight (deep in a tunnel, in an unloaded chunk).
        String key = "worker_" + job.name().toLowerCase();
        boolean registered = v.data().hasUUID(key);
        if (worker == null && registered && level.getEntity(v.data().getUUID(key)) instanceof VillageWorker w) {
            worker = w;
        }
        if (worker == null && registered) {
            return true;
        }
        Building home = v.buildings().stream()
                .filter(b -> b.isComplete() && b.workerId() == null && job.workplace().equals(b.typeId()))
                .findFirst().orElse(null);
        if (worker == null && needed) {
            worker = convert(level, v, villagers, job, home, home != null ? home.entrance() : v.center());
            if (worker != null) {
                v.data().putUUID(key, worker.getUUID());
                if (home != null) {
                    home.setWorkerId(worker.getUUID());
                }
            }
            return worker == null;
        }
        // Hired before the workplace existed: moves in once it is built.
        if (worker != null && home != null && !worker.hasWorkplace()) {
            worker.setWorkplace(v, home);
            home.setWorkerId(worker.getUUID());
            v.markDirty();
        }
        return true;
    }

    /** Turns the unemployed adult villager nearest to {@code near} into a worker; null if there is none. */
    private static VillageWorker convert(ServerLevel level, Village v, List<Villager> villagers, WorkerJob job,
                                         Building workplace, BlockPos near) {
        Villager candidate = villagers.stream()
                .filter(x -> x.isAlive() && !x.isBaby() && !x.isTrading()
                        && x.getVillagerData().getProfession() == VillagerProfession.NONE)
                .min(Comparator.comparingDouble(x -> x.distanceToSqr(near.getCenter())))
                .orElse(null);
        if (candidate == null) {
            return null;
        }
        VillageWorker worker = LVEntities.WORKER.get().create(level);
        if (worker == null) {
            return null;
        }
        worker.moveTo(candidate.getX(), candidate.getY(), candidate.getZ(), candidate.getYRot(), 0);
        worker.setCustomName(candidate.getCustomName());
        worker.assign(job, v, workplace);
        Villager.POI_MEMORIES.keySet().forEach(candidate::releasePoi); // free its bed and job site
        candidate.discard();
        level.addFreshEntity(worker);
        villagers.remove(candidate);
        v.markDirty();
        LivingVillages.LOGGER.info("Village {} hired a {}", v.id().toString().substring(0, 8),
                job.name().toLowerCase());
        return worker;
    }
}
