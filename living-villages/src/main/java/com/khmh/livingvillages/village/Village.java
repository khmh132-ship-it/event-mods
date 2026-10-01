package com.khmh.livingvillages.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A village as a data object, anchored to a bell. Lives in {@link VillageManager}, not in any entity,
 * so it keeps existing (and later keeps simulating) while its chunks are unloaded.
 */
public class Village {
    public static final int DEFAULT_RADIUS = 48;

    private final UUID id;
    private final BlockPos center;
    private int radius = DEFAULT_RADIUS;
    private int level = 1;
    private int population;
    private long lastSeenLoaded;
    private final VillageStorage storage = new VillageStorage();
    private final Map<UUID, Integer> reputation = new HashMap<>();
    private Runnable onChange = () -> {};

    public Village(UUID id, BlockPos center) {
        this.id = id;
        this.center = center.immutable();
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
        storage.setOnChange(onChange);
    }

    public UUID id() {
        return id;
    }

    public BlockPos center() {
        return center;
    }

    public int radius() {
        return radius;
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

    public long lastSeenLoaded() {
        return lastSeenLoaded;
    }

    /** Called while the village is loaded, with the live villager count. */
    void observe(int population, long gameTime) {
        if (this.population != population) {
            this.population = population;
            onChange.run();
        }
        this.lastSeenLoaded = gameTime;
    }

    public VillageStorage storage() {
        return storage;
    }

    public int reputation(UUID player) {
        return reputation.getOrDefault(player, 0);
    }

    public void addReputation(UUID player, int delta) {
        reputation.merge(player, delta, Integer::sum);
        onChange.run();
    }

    public boolean contains(BlockPos pos) {
        return center.distSqr(pos) <= (double) radius * radius;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putLong("center", center.asLong());
        tag.putInt("radius", radius);
        tag.putInt("level", level);
        tag.putInt("population", population);
        tag.putLong("lastSeenLoaded", lastSeenLoaded);
        tag.put("storage", storage.save());
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
        v.lastSeenLoaded = tag.getLong("lastSeenLoaded");
        v.storage.load(tag.getList("storage", Tag.TAG_COMPOUND));
        ListTag rep = tag.getList("reputation", Tag.TAG_COMPOUND);
        for (int i = 0; i < rep.size(); i++) {
            CompoundTag r = rep.getCompound(i);
            v.reputation.put(r.getUUID("player"), r.getInt("value"));
        }
        return v;
    }
}
