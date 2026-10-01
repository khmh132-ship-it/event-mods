package com.khmh.livingvillages.village;

import com.khmh.livingvillages.LivingVillages;
import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.building.BuildingType;
import com.khmh.livingvillages.building.PathBuilder;
import com.khmh.livingvillages.building.SiteFinder;
import com.khmh.livingvillages.config.LVConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A village as a data object, anchored to a bell. Lives in {@link VillageManager}, not in any entity,
 * so it keeps existing (and keeps producing and building) while its chunks are unloaded.
 */
public class Village {
    public static final int DEFAULT_RADIUS = 48;

    private final UUID id;
    private final BlockPos center;
    private int radius = DEFAULT_RADIUS;
    private int level = 1;
    private int population;
    private int beds;
    private Map<String, Integer> professions = new HashMap<>();
    private long lastSeenLoaded;
    private long lastProduction;
    private long lastPlan;
    private boolean autoGrowth = true;
    @Nullable
    private String waitingFor;
    private final VillageStorage storage = new VillageStorage();
    private final List<Building> buildings = new ArrayList<>();
    private final Map<UUID, Integer> reputation = new HashMap<>();
    private final Map<Item, Double> fractions = new HashMap<>();
    private double foodDebt;
    private List<String> lastPlanReport = List.of();
    private final Map<UUID, Long> workSeen = new HashMap<>();
    private long builderSeen = Long.MIN_VALUE / 2;
    private Runnable onChange = () -> {};

    public Village(UUID id, BlockPos center) {
        this.id = id;
        this.center = center.immutable();
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
        storage.setOnChange(onChange);
    }

    public void markDirty() {
        onChange.run();
    }

    public UUID id() {
        return id;
    }

    public BlockPos center() {
        return center;
    }

    /** The village claims more land as it grows. */
    public int radius() {
        return radius + 16 * (level - 1);
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
        onChange.run();
    }

    public int population() {
        return population;
    }

    public int beds() {
        return beds;
    }

    public Map<String, Integer> professions() {
        return Collections.unmodifiableMap(professions);
    }

    public long lastSeenLoaded() {
        return lastSeenLoaded;
    }

    /** Called while the village is loaded, with live counts. */
    void observe(int population, int beds, Map<String, Integer> professions, long gameTime) {
        if (this.population != population || this.beds != beds || !this.professions.equals(professions)) {
            this.population = population;
            this.beds = beds;
            this.professions = new HashMap<>(professions);
            recalcLevel();
            onChange.run();
        }
        this.lastSeenLoaded = gameTime;
    }

    public long lastProduction() {
        return lastProduction;
    }

    void setLastProduction(long t) {
        lastProduction = t;
        onChange.run();
    }

    public long lastPlan() {
        return lastPlan;
    }

    void setLastPlan(long t) {
        lastPlan = t;
    }

    public boolean autoGrowth() {
        return autoGrowth;
    }

    public void setAutoGrowth(boolean autoGrowth) {
        this.autoGrowth = autoGrowth;
        onChange.run();
    }

    public List<String> lastPlanReport() {
        return lastPlanReport;
    }

    void setLastPlanReport(List<String> report) {
        lastPlanReport = List.copyOf(report);
    }

    @Nullable
    public String waitingFor() {
        return waitingFor;
    }

    void setWaitingFor(@Nullable String typeId) {
        waitingFor = typeId;
    }

    public VillageStorage storage() {
        return storage;
    }

    public int storageCap() {
        return LVConfig.STORAGE_CAP.get() * (1 + countGroupComplete("warehouse"));
    }

    /** Adds a fractional amount and returns the whole units now available. */
    int accumulate(Item item, double amount) {
        double total = fractions.getOrDefault(item, 0.0) + amount;
        int whole = (int) Math.floor(total);
        fractions.put(item, total - whole);
        return whole;
    }

    /** Same for food demand: whole items to eat this cycle. */
    int accumulateNeed(double amount) {
        foodDebt += amount;
        int whole = (int) Math.floor(foodDebt);
        foodDebt -= whole;
        return whole;
    }

    /** A builder of this village is alive and loaded. */
    public void noteBuilder(long now) {
        builderSeen = now;
    }

    public boolean hasBuilder(long now) {
        return now - builderSeen <= 200;
    }

    /** A worker is busy at (or for) a building right now. */
    public void reportWorking(UUID buildingId, long now) {
        workSeen.put(buildingId, now);
    }

    public boolean isWorkedRecently(UUID buildingId, long now, long window) {
        Long seen = workSeen.get(buildingId);
        return seen != null && now - seen <= window;
    }

    public List<Building> buildings() {
        return Collections.unmodifiableList(buildings);
    }

    public Building addBuilding(BuildingType type, SiteFinder.Site site, long now) {
        Building b = new Building(UUID.randomUUID(), type.id(), site.origin(), site.rotation(), site.groundY(),
                site.box(), site.entrance(), Building.Phase.PREPARE, 0, now);
        buildings.add(b);
        onChange.run();
        return b;
    }

    public void onBuildingComplete(ServerLevel level, Building b) {
        LivingVillages.LOGGER.info("Village {} finished {} at {}", id.toString().substring(0, 8), b.typeId(),
                b.origin());
        PathBuilder.connect(level, b.entrance(), center, radius() * 2);
        recalcLevel();
        onChange.run();
    }

    public int countType(String typeId) {
        return (int) buildings.stream().filter(b -> b.typeId().equals(typeId)).count();
    }

    public int countGroup(String group) {
        return (int) buildings.stream().filter(b -> b.type() != null && b.type().group().equals(group)).count();
    }

    public int countGroupComplete(String group) {
        return (int) buildings.stream()
                .filter(b -> b.isComplete() && b.type() != null && b.type().group().equals(group)).count();
    }

    private void recalcLevel() {
        int complete = (int) buildings.stream().filter(Building::isComplete).count();
        int target = 1;
        if (population >= 6 && complete >= 4) {
            target = 2;
        }
        if (population >= 12 && complete >= 10 && countGroupComplete("warehouse") > 0) {
            target = 3;
        }
        if (target > level) {
            LivingVillages.LOGGER.info("Village {} reached level {}", id.toString().substring(0, 8), target);
            level = target;
        }
    }

    public int reputation(UUID player) {
        return reputation.getOrDefault(player, 0);
    }

    public void addReputation(UUID player, int delta) {
        reputation.merge(player, delta, Integer::sum);
        onChange.run();
    }

    public boolean contains(BlockPos pos) {
        return center.distSqr(pos) <= (double) radius() * radius();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putLong("center", center.asLong());
        tag.putInt("radius", radius);
        tag.putInt("level", level);
        tag.putInt("population", population);
        tag.putInt("beds", beds);
        CompoundTag prof = new CompoundTag();
        professions.forEach(prof::putInt);
        tag.put("professions", prof);
        tag.putLong("lastSeenLoaded", lastSeenLoaded);
        tag.putLong("lastProduction", lastProduction);
        tag.putBoolean("autoGrowth", autoGrowth);
        tag.put("storage", storage.save());
        ListTag list = new ListTag();
        buildings.forEach(b -> list.add(b.save()));
        tag.put("buildings", list);
        CompoundTag frac = new CompoundTag();
        fractions.forEach((item, v) -> frac.putDouble(BuiltInRegistries.ITEM.getKey(item).toString(), v));
        tag.put("fractions", frac);
        tag.putDouble("foodDebt", foodDebt);
        ListTag rep = new ListTag();
        reputation.forEach((player, value) -> {
            CompoundTag r = new CompoundTag();
            r.putUUID("player", player);
            r.putInt("value", value);
            rep.add(r);
        });
        tag.put("reputation", rep);
        return tag;
    }

    public static Village load(CompoundTag tag) {
        Village v = new Village(tag.getUUID("id"), BlockPos.of(tag.getLong("center")));
        v.radius = tag.contains("radius") ? tag.getInt("radius") : DEFAULT_RADIUS;
        v.level = Math.max(1, tag.getInt("level"));
        v.population = tag.getInt("population");
        v.beds = tag.getInt("beds");
        CompoundTag prof = tag.getCompound("professions");
        for (String k : prof.getAllKeys()) {
            v.professions.put(k, prof.getInt(k));
        }
        v.lastSeenLoaded = tag.getLong("lastSeenLoaded");
        v.lastProduction = tag.getLong("lastProduction");
        v.autoGrowth = !tag.contains("autoGrowth") || tag.getBoolean("autoGrowth");
        v.storage.load(tag.getList("storage", Tag.TAG_COMPOUND));
        ListTag list = tag.getList("buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            v.buildings.add(Building.load(list.getCompound(i)));
        }
        CompoundTag frac = tag.getCompound("fractions");
        for (String k : frac.getAllKeys()) {
            ResourceLocation key = ResourceLocation.tryParse(k);
            if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
                v.fractions.put(BuiltInRegistries.ITEM.get(key), frac.getDouble(k));
            }
        }
        v.foodDebt = tag.getDouble("foodDebt");
        ListTag rep = tag.getList("reputation", Tag.TAG_COMPOUND);
        for (int i = 0; i < rep.size(); i++) {
            CompoundTag r = rep.getCompound(i);
            v.reputation.put(r.getUUID("player"), r.getInt("value"));
        }
        return v;
    }
}
