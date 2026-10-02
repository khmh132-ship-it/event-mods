package com.khmh.livingvillages.village;

import com.khmh.livingvillages.LivingVillages;
import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.building.CostKey;
import com.khmh.livingvillages.stock.Stockpile;
import com.khmh.livingvillages.entity.LVEntities;
import com.khmh.livingvillages.entity.VillageWorker;
import com.khmh.livingvillages.entity.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
        // How long each has been standing about: a moment between two tasks is not idleness.
        for (VillageWorker w : workers) {
            if (idleNow(w)) {
                if (!w.getPersistentData().contains("lvIdleSince")) {
                    w.getPersistentData().putLong("lvIdleSince", level.getGameTime());
                }
            } else {
                w.getPersistentData().remove("lvIdleSince");
            }
        }
        // Vanilla professionals come under the village first: they keep their looks and do their job for real.
        for (Villager farmer : List.copyOf(villagers)) {
            if (!farmer.isAlive() || farmer.isBaby()) {
                continue;
            }
            VillagerProfession p = farmer.getVillagerData().getProfession();
            WorkerJob job = p == VillagerProfession.FARMER ? WorkerJob.FARMER
                    : p == VillagerProfession.SHEPHERD ? WorkerJob.SHEPHERD
                    : p == VillagerProfession.BUTCHER ? WorkerJob.BUTCHER
                    : p == VillagerProfession.MASON ? WorkerJob.MASON
                    : p == VillagerProfession.TOOLSMITH ? WorkerJob.TOOLSMITH
                    : p == VillagerProfession.WEAPONSMITH ? WorkerJob.WEAPONSMITH
                    : p == VillagerProfession.ARMORER ? WorkerJob.ARMORER
                    : p == VillagerProfession.FLETCHER ? WorkerJob.FLETCHER
                    : p == VillagerProfession.LEATHERWORKER ? WorkerJob.LEATHERWORKER
                    : p == VillagerProfession.FISHERMAN ? WorkerJob.FISHERMAN
                    : p == VillagerProfession.CLERIC ? WorkerJob.CLERIC
                    : p == VillagerProfession.LIBRARIAN ? WorkerJob.LIBRARIAN
                    : p == VillagerProfession.CARTOGRAPHER ? WorkerJob.CARTOGRAPHER
                    : p == VillagerProfession.NITWIT ? WorkerJob.CARRIER : null;
            if (job != null) {
                takeOver(level, v, villagers, farmer, job);
            }
        }


        // A village that builds always has a builder, one that needs things made an apprentice, workplace or not.
        boolean building = v.buildings().stream().anyMatch(b -> !b.isComplete());
        boolean orders = v.requests().stream().anyMatch(r -> r.remaining() > 0 && r.unobtainableSince() < 0
                && com.khmh.livingvillages.economy.Trades.isMine(WorkerJob.APPRENTICE, r.item(), v));
        // Wood is the first thing every village needs: a lumberjack works from the bell until he has a hut.
        boolean woodShort = CostKey.Wood.INSTANCE.available(v.stock(level)) < 512
                || v.buildings().stream().anyMatch(b -> "lumberjack_hut".equals(b.typeId()) && b.isComplete());
        // Likewise stone: a miner digs his own quarry until there is a mine.
        boolean stoneShort = CostKey.Stone.INSTANCE.available(v.stock(level)) < 128
                || v.buildings().stream().anyMatch(b -> "mine".equals(b.typeId()) && b.isComplete());
        // Labour exchange: the most pressing jobs first; with too few people a worker is moved over from a job
        // that matters less right now (a camp of two: one builds, the other fetches wood or makes what is asked).
        List<WorkerJob> priority = new java.util.ArrayList<>();
        java.util.Map<WorkerJob, Boolean> needed = new java.util.EnumMap<>(WorkerJob.class);
        // A builder standing about waiting for materials is better off fetching them himself.
        boolean stalled = workers.stream().anyMatch(w -> w.job() == WorkerJob.BUILDER
                && w.status().contains("waiting for materials"));
        // ...and once he has gone for them, he keeps at it a good while before coming back to wait again.
        if (stalled) {
            v.data().putLong("builderStall", level.getGameTime());
        }
        // (only if there is something he can fetch: wood or stone; waiting for a craft he can do nothing about)
        boolean recentlyStalled = level.getGameTime() - v.data().getLong("builderStall") < 6000
                && v.data().contains("builderStall")
                && v.requests().stream().anyMatch(r -> r.requester().startsWith("build:") && r.unobtainableSince() >= 0);
        needed.put(WorkerJob.BUILDER, building && !stalled && !recentlyStalled);
        // An apprentice with nothing he can make is no use as one.
        boolean idleApprentice = workers.stream().anyMatch(w -> w.job() == WorkerJob.APPRENTICE
                && idleFor(level, w) >= 1200);
        needed.put(WorkerJob.APPRENTICE, orders && !idleApprentice);
        needed.put(WorkerJob.LUMBERJACK, woodShort);
        needed.put(WorkerJob.MINER, stoneShort);
        // Hysteresis, so the second pair of hands does not flip between axe and workbench every minute.
        int wood = CostKey.Wood.INSTANCE.available(v.stock(level));
        boolean woodInHand = wood >= 64 || wood >= 16 && v.data().getBoolean("woodInHand");
        v.data().putBoolean("woodInHand", woodInHand);
        // Food: a hunter while the stores are thin and there is game about, a farmer once there are fields.
        int food = v.stock(level).totals().entrySet().stream()
                .filter(e -> e.getKey().isEdible()).mapToInt(Map.Entry::getValue).sum()
                + v.stock(level).count(net.minecraft.world.item.Items.WHEAT) / 3; // bread to be
        boolean foodLow = food < Math.max(2, v.population()) * 6;
        boolean game = !level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,
                new net.minecraft.world.phys.AABB(v.center()).inflate(v.radius() + 16), a -> a.isAlive() && !a.isBaby()
                        && (a instanceof net.minecraft.world.entity.animal.Cow || a instanceof net.minecraft.world.entity.animal.Pig
                        || a instanceof net.minecraft.world.entity.animal.Chicken || a instanceof net.minecraft.world.entity.animal.Sheep
                        || a instanceof net.minecraft.world.entity.animal.Rabbit)).isEmpty();
        boolean fields = v.buildings().stream().anyMatch(b -> b.isComplete()
                && com.khmh.livingvillages.building.BuildingTypes.get(b.typeId()) != null
                && "farm".equals(com.khmh.livingvillages.building.BuildingTypes.get(b.typeId()).group()));
        // A hunter who finds nothing he can get at is no use as one.
        boolean idleHunter = workers.stream().anyMatch(w -> w.job() == WorkerJob.BUTCHER && idleFor(level, w) >= 1200);
        needed.put(WorkerJob.BUTCHER, foodLow && game && !idleHunter);
        needed.put(WorkerJob.FARMER, fields && com.khmh.livingvillages.entity.FarmerWorkGoal.hasWork(level, v));
        if (foodLow) {
            priority.add(WorkerJob.FARMER);
            priority.add(WorkerJob.BUTCHER);
        }
        priority.add(WorkerJob.BUILDER);
        if (!foodLow) {
            priority.add(WorkerJob.FARMER);
        }
        if (woodInHand) {
            priority.add(WorkerJob.APPRENTICE);
            priority.add(WorkerJob.LUMBERJACK);
        } else {
            priority.add(WorkerJob.LUMBERJACK);
            priority.add(WorkerJob.APPRENTICE);
        }
        priority.add(WorkerJob.MINER);
        // A building the village is saving up for and short of stone: the quarry comes before the rest.
        if (v.waitingFor() != null && v.lastPlanReport().stream().findFirst()
                .map(l -> l.contains("more stone")).orElse(false)) {
            priority.remove(WorkerJob.MINER);
            priority.add(priority.indexOf(WorkerJob.BUILDER) + 1, WorkerJob.MINER);
            needed.put(WorkerJob.MINER, true);
        }
        if (!foodLow) {
            priority.add(WorkerJob.BUTCHER);
        }
        StringBuilder board = new StringBuilder();
        for (WorkerJob j : priority) {
            board.append(j.name().toLowerCase()).append(needed.getOrDefault(j, false) ? "+ " : "- ");
        }
        v.data().putString("exchange", board.toString().trim() + (foodLow ? " (food low)" : ""));
        for (int i = 0; i < priority.size(); i++) {
            WorkerJob job = priority.get(i);
            if (!villageWide(level, v, villagers, workers, job, needed.get(job))) {
                return;
            }
            if (needed.get(job) && !hasJob(level, v, workers, job)
                    && reassign(level, v, workers, job, priority.subList(i + 1, priority.size()), priority, needed)) {
                return;
            }
        }

        // Workplaces (storekeeper at the warehouse, carpenter at the sawmill...) before guards and carriers.
        for (Building b : v.buildings()) {
            WorkerJob job = WorkerJob.forWorkplace(b.typeId());
            if (job == null || !b.isComplete() || b.workerId() != null) {
                continue;
            }
            VillageWorker worker = convert(level, v, villagers, job, b, b.entrance());
            if (worker == null) {
                break; // nobody free; try again on the next scan
            }
            b.setWorkerId(worker.getUUID());
            worker.getPersistentData().putBoolean("lvPermanent", true); // his workplace, for good
            return;
        }

        // Guards: one for every six villagers (at least one from four on), two more while the alarm is up.
        int wanted = v.population() < 4 ? 0 : Math.max(1, v.population() / 6) + (v.alarm() ? 2 : 0);
        long guards = workers.stream().filter(w -> w.job() == WorkerJob.GUARD).count();
        if (guards < wanted) {
            Building barracks = v.buildings().stream()
                    .filter(b -> b.isComplete() && "barracks".equals(b.typeId())).findFirst().orElse(null);
            if (convert(level, v, villagers, WorkerJob.GUARD, barracks, v.center()) != null) {
                return;
            }
        }

        // Carriers: one for every ten villagers once there is a warehouse and something to haul.
        long carriers = workers.stream().filter(w -> w.job() == WorkerJob.CARRIER).count();
        if (carriers < 1 + v.population() / 10 && !Stockpile.outlying(level, v).isEmpty()
                && convert(level, v, villagers, WorkerJob.CARRIER, null, v.center()) != null) {
            return;
        }

    }

    /** Eight minutes at least in a job before being moved again: walking about between jobs is no work. */
    private static boolean settled(ServerLevel level, VillageWorker w) {
        if (w.getPersistentData().getBoolean("lvPermanent")) {
            return false;
        }
        long inJob = level.getGameTime() - w.getPersistentData().getLong("lvJobSince");
        // Nothing to do for a good minute on end, and a fair while in the job: free to go sooner.
        return inJob >= 9600 || inJob >= 2400 && idleFor(level, w) >= 1200;
    }

    private static boolean idleNow(VillageWorker w) {
        String s = w.status();
        return s.startsWith("pick") || s.startsWith("none") || s.startsWith("idle");
    }

    /** Ticks he has been idle without a break (0 if he is busy). */
    private static long idleFor(ServerLevel level, VillageWorker w) {
        return w.getPersistentData().contains("lvIdleSince")
                ? level.getGameTime() - w.getPersistentData().getLong("lvIdleSince") : 0;
    }

    /** A gatherer on his way back with a load: he finishes the trip first. */
    private static boolean midTrip(VillageWorker w) {
        return (w.job() == WorkerJob.LUMBERJACK || w.job() == WorkerJob.FARMER || w.job() == WorkerJob.BUTCHER)
                && w.carried() > 16;
    }

    private static boolean hasJob(ServerLevel level, Village v, List<VillageWorker> workers, WorkerJob job) {
        return workers.stream().anyMatch(w -> w.job() == job) || v.data().hasUUID("worker_" + job.name().toLowerCase());
    }

    /**
     * Moves a worker from a less pressing village-wide job (one not needed at all first) over to {@code job}.
     * At most once every four minutes per village, so people do not flap between jobs.
     */
    private static boolean reassign(ServerLevel level, Village v, List<VillageWorker> workers, WorkerJob job,
                                    List<WorkerJob> lower, List<WorkerJob> all,
                                    java.util.Map<WorkerJob, Boolean> needed) {
        long now = level.getGameTime();
        if (now - v.data().getLong("lastReassign") < 4800) {
            return false;
        }
        VillageWorker donor = null;
        // Someone whose job is not needed at all right now, whatever its rank; else the least pressing one below.
        for (WorkerJob from : all) {
            if (from != job && donor == null && !needed.getOrDefault(from, false)) {
                donor = workers.stream().filter(w -> w.job() == from && settled(level, w) && !midTrip(w)).findFirst().orElse(null);
            }
        }
        // Taking someone off work that is wanted too only robs Peter to pay Paul: a camp of two or three has to
        // juggle, a bigger village keeps people at their jobs for a good day before moving them.
        boolean juggle = v.population() <= 3;
        for (int i = lower.size() - 1; i >= 0 && donor == null; i--) {
            WorkerJob from = lower.get(i);
            // Not in the middle of a trip: he finishes it (and unloads) first.
            donor = workers.stream().filter(w -> w.job() == from && settled(level, w) && !midTrip(w)
                    && (juggle || idleFor(level, w) >= 1200
                    || now - w.getPersistentData().getLong("lvJobSince") >= 24000)).findFirst().orElse(null);
        }
        if (donor == null) {
            return false;
        }
        WorkerJob from = donor.job();
        v.data().remove("worker_" + from.name().toLowerCase());
        donor.workplace().ifPresent(b -> b.setWorkerId(null));
        Building home = v.buildings().stream()
                .filter(b -> b.isComplete() && b.workerId() == null && job.workplace().equals(b.typeId()))
                .findFirst().orElse(null);
        donor.assign(job, v, home);
        donor.getPersistentData().putLong("lvJobSince", level.getGameTime());
        if (home != null) {
            home.setWorkerId(donor.getUUID());
        }
        v.data().putUUID("worker_" + job.name().toLowerCase(), donor.getUUID());
        v.data().putLong("lastReassign", now);
        v.markDirty();
        LivingVillages.LOGGER.info("Village {}: a {} turns {}", v.id().toString().substring(0, 8),
                from.name().toLowerCase(), job.name().toLowerCase());
        return true;
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

    /** Puts a particular villager (keeping its profession's look) under the village's own AI. */
    private static void takeOver(ServerLevel level, Village v, List<Villager> villagers, Villager villager,
                                 WorkerJob job) {
        VillageWorker worker = LVEntities.WORKER.get().create(level);
        if (worker == null) {
            return;
        }
        worker.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), 0);
        worker.setCustomName(villager.getCustomName());
        worker.assign(job, v, null);
        worker.getPersistentData().putBoolean("lvPermanent", true); // took a job block: his for good (trading later)
        for (ItemStack s : villager.getInventory().removeAllItems()) {
            worker.getInventory().addItem(s); // its seeds and harvest come along
        }
        Villager.POI_MEMORIES.keySet().forEach(villager::releasePoi);
        villager.discard();
        level.addFreshEntity(worker);
        villagers.remove(villager);
        v.markDirty();
        LivingVillages.LOGGER.info("Village {} took over a {}", v.id().toString().substring(0, 8),
                job.name().toLowerCase());
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
        worker.getPersistentData().putLong("lvJobSince", level.getGameTime());
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
