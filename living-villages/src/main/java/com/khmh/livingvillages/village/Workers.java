package com.khmh.livingvillages.village;

import com.khmh.livingvillages.LivingVillages;
import com.khmh.livingvillages.building.Building;
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
        AABB box = new AABB(v.center()).inflate(v.radius() + 16);
        return level.getEntitiesOfClass(VillageWorker.class, box,
                w -> w.isAlive() && v.id().equals(w.villageId()));
    }

    static void hire(ServerLevel level, Village v, List<Villager> villagers, List<VillageWorker> workers) {
        VillageWorker builder = workers.stream().filter(w -> w.job() == WorkerJob.BUILDER).findFirst().orElse(null);
        Building workshop = v.buildings().stream()
                .filter(b -> b.isComplete() && b.workerId() == null && "builder_workshop".equals(b.typeId()))
                .findFirst().orElse(null);

        // A village that builds always has a builder, workshop or not.
        if (builder == null && v.buildings().stream().anyMatch(b -> !b.isComplete())) {
            BlockPos site = v.buildings().stream().filter(b -> !b.isComplete()).findFirst().get().entrance();
            builder = convert(level, v, villagers, WorkerJob.BUILDER, workshop, site);
            if (builder != null && workshop != null) {
                workshop.setWorkerId(builder.getUUID());
            }
            return;
        }
        // A builder hired before the workshop existed moves in once it is built.
        if (builder != null && workshop != null && !builder.hasWorkplace()) {
            builder.setWorkplace(v, workshop);
            workshop.setWorkerId(builder.getUUID());
            v.markDirty();
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
