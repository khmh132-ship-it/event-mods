package com.khmh.livingvillages.village;

import com.khmh.livingvillages.LivingVillages;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** All villages of one dimension, persisted in {@code data/livingvillages_villages.dat}. */
public class VillageManager extends SavedData {
    private static final String DATA_NAME = LivingVillages.MODID + "_villages";

    private final Map<UUID, Village> villages = new LinkedHashMap<>();

    public static VillageManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VillageManager::load, VillageManager::new, DATA_NAME);
    }

    public Collection<Village> all() {
        return Collections.unmodifiableCollection(villages.values());
    }

    public Optional<Village> byId(UUID id) {
        return Optional.ofNullable(villages.get(id));
    }

    /** The village whose area contains {@code pos}; the nearest center wins if areas overlap. */
    public Optional<Village> at(BlockPos pos) {
        Village best = null;
        double bestDist = Double.MAX_VALUE;
        for (Village v : villages.values()) {
            double d = v.center().distSqr(pos);
            if (v.contains(pos) && d < bestDist) {
                best = v;
                bestDist = d;
            }
        }
        return Optional.ofNullable(best);
    }

    public Optional<Village> byBell(BlockPos bell) {
        return villages.values().stream().filter(v -> v.center().equals(bell)).findFirst();
    }

    public Village create(BlockPos bell) {
        Village v = new Village(UUID.randomUUID(), bell);
        // No starter stock: whatever the village has, it gathers itself.
        add(v);
        setDirty();
        LivingVillages.LOGGER.info("Village {} founded at {}", v.id(), bell);
        return v;
    }

    public void remove(Village v) {
        if (villages.remove(v.id()) != null) {
            setDirty();
            LivingVillages.LOGGER.info("Village {} at {} disbanded", v.id(), v.center());
        }
    }

    public void add(Village v) {
        v.setOnChange(this::setDirty);
        villages.put(v.id(), v);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        villages.values().forEach(v -> list.add(v.save()));
        tag.put("villages", list);
        return tag;
    }

    private static VillageManager load(CompoundTag tag) {
        VillageManager m = new VillageManager();
        ListTag list = tag.getList("villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            m.add(Village.load(list.getCompound(i)));
        }
        return m;
    }
}
