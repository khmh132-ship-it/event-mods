package com.khmh.livingvillages.village;

import com.khmh.livingvillages.LivingVillages;
import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.entity.LVEntities;
import com.khmh.livingvillages.entity.VillageWorker;
import com.khmh.livingvillages.entity.WorkerJob;
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

    static void hire(ServerLevel level, Village v, List<Villager> villagers) {
        for (Building b : v.buildings()) {
            WorkerJob job = WorkerJob.forWorkplace(b.typeId());
            if (job == null || !b.isComplete() || b.workerId() != null) {
                continue;
            }
            Villager candidate = villagers.stream()
                    .filter(x -> x.isAlive() && !x.isBaby() && !x.isTrading()
                            && x.getVillagerData().getProfession() == VillagerProfession.NONE)
                    .min(Comparator.comparingDouble(x -> x.distanceToSqr(b.entrance().getCenter())))
                    .orElse(null);
            if (candidate == null) {
                return; // nobody free; try again on the next scan
            }
            VillageWorker worker = LVEntities.WORKER.get().create(level);
            if (worker == null) {
                return;
            }
            worker.moveTo(candidate.getX(), candidate.getY(), candidate.getZ(), candidate.getYRot(), 0);
            worker.setCustomName(candidate.getCustomName());
            worker.assign(job, v, b);
            Villager.POI_MEMORIES.keySet().forEach(candidate::releasePoi); // free its bed and job site
            candidate.discard();
            level.addFreshEntity(worker);
            villagers.remove(candidate);
            b.setWorkerId(worker.getUUID());
            v.markDirty();
            LivingVillages.LOGGER.info("Village {} hired a {}", v.id().toString().substring(0, 8),
                    job.name().toLowerCase());
        }
    }
}
