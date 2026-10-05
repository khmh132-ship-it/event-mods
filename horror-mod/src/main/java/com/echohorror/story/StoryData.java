package com.echohorror.story;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** World-global story state (saved with the overworld). */
public class StoryData extends SavedData {
    public record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
        public boolean contains(Vec3 p) {
            return p.x >= x1 && p.x < x2 + 1 && p.y >= y1 && p.y < y2 + 1 && p.z >= z1 && p.z < z2 + 1;
        }

        public AABB aabb() {
            return new AABB(x1, y1, z1, x2 + 1, y2 + 1, z2 + 1);
        }

        public BlockPos center() {
            return new BlockPos((x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2);
        }
    }

    public int chapter;
    public long lastCountDay = -1;
    public long echoDay = -1;
    public int nights;
    public final Map<String, BlockPos> pos = new HashMap<>();
    public final Map<String, List<BlockPos>> lists = new HashMap<>();
    public final Map<String, Box> regions = new LinkedHashMap<>();
    public final LinkedHashSet<String> notes = new LinkedHashSet<>();
    public final Set<String> flags = new HashSet<>();
    public final Set<UUID> kits = new HashSet<>();
    public final Set<Long> switchesOn = new HashSet<>();

    public static StoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(StoryData::load, StoryData::new, "echohorror_story");
    }

    public BlockPos get(String key) {
        return pos.get(key);
    }

    public void put(String key, BlockPos p) {
        pos.put(key, p.immutable());
        setDirty();
    }

    public List<BlockPos> list(String key) {
        return lists.computeIfAbsent(key, k -> new ArrayList<>());
    }

    public boolean flag(String f) {
        return flags.contains(f);
    }

    /** Sets a flag; returns true if it was newly set. */
    public boolean setFlag(String f) {
        boolean r = flags.add(f);
        if (r) setDirty();
        return r;
    }

    public void region(String name, Box b) {
        regions.put(name, b);
        setDirty();
    }

    public boolean in(String region, Vec3 p) {
        Box b = regions.get(region);
        return b != null && b.contains(p);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("chapter", chapter);
        tag.putLong("lastCountDay", lastCountDay);
        tag.putLong("echoDay", echoDay);
        tag.putInt("nights", nights);
        CompoundTag p = new CompoundTag();
        pos.forEach((k, v) -> p.put(k, NbtUtils.writeBlockPos(v)));
        tag.put("pos", p);
        CompoundTag l = new CompoundTag();
        lists.forEach((k, v) -> {
            ListTag lt = new ListTag();
            for (BlockPos b : v) lt.add(NbtUtils.writeBlockPos(b));
            l.put(k, lt);
        });
        tag.put("lists", l);
        CompoundTag r = new CompoundTag();
        regions.forEach((k, b) -> r.putIntArray(k, new int[]{b.x1, b.y1, b.z1, b.x2, b.y2, b.z2}));
        tag.put("regions", r);
        ListTag n = new ListTag();
        for (String s : notes) n.add(StringTag.valueOf(s));
        tag.put("notes", n);
        ListTag f = new ListTag();
        for (String s : flags) f.add(StringTag.valueOf(s));
        tag.put("flags", f);
        ListTag k = new ListTag();
        for (UUID u : kits) k.add(NbtUtils.createUUID(u));
        tag.put("kits", k);
        tag.putLongArray("switchesOn", switchesOn.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    public static StoryData load(CompoundTag tag) {
        StoryData d = new StoryData();
        d.chapter = tag.getInt("chapter");
        d.lastCountDay = tag.getLong("lastCountDay");
        d.echoDay = tag.contains("echoDay") ? tag.getLong("echoDay") : -1;
        d.nights = tag.getInt("nights");
        CompoundTag p = tag.getCompound("pos");
        for (String k : p.getAllKeys()) d.pos.put(k, NbtUtils.readBlockPos(p.getCompound(k)));
        CompoundTag l = tag.getCompound("lists");
        for (String k : l.getAllKeys()) {
            List<BlockPos> list = new ArrayList<>();
            for (Tag t : l.getList(k, Tag.TAG_COMPOUND)) list.add(NbtUtils.readBlockPos((CompoundTag) t));
            d.lists.put(k, list);
        }
        CompoundTag r = tag.getCompound("regions");
        for (String k : r.getAllKeys()) {
            int[] a = r.getIntArray(k);
            if (a.length == 6) d.regions.put(k, new Box(a[0], a[1], a[2], a[3], a[4], a[5]));
        }
        for (Tag t : tag.getList("notes", Tag.TAG_STRING)) d.notes.add(t.getAsString());
        for (Tag t : tag.getList("flags", Tag.TAG_STRING)) d.flags.add(t.getAsString());
        for (Tag t : tag.getList("kits", Tag.TAG_INT_ARRAY)) d.kits.add(NbtUtils.loadUUID(t));
        for (long v : tag.getLongArray("switchesOn")) d.switchesOn.add(v);
        return d;
    }
}
