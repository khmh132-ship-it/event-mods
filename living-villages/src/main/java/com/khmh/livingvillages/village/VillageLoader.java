package com.khmh.livingvillages.village;

import com.khmh.livingvillages.LivingVillages;
import com.khmh.livingvillages.config.LVConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps villages going while nobody is around: each village holds its own chunks loaded and ticking (as a
 * player standing at the bell would), so its people really work, build, farm and have children, and new
 * buildings appear on their own. The biggest villages first, up to a configured number.
 */
public final class VillageLoader {
    /** village -> (bell, forced chunks) */
    private static final Map<UUID, Map.Entry<BlockPos, Set<Long>>> FORCED = new HashMap<>();

    private static final int NEW_PER_SCAN = 6;

    private VillageLoader() {
    }

    /** Old tickets from a previous run: kept only for bells that still have a village. */
    public static void registerValidation() {
        ForgeChunkManager.setForcedChunkLoadingCallback(LivingVillages.MODID, (level, helper) -> {
            VillageManager manager = VillageManager.get(level);
            // Tickets that followed workers about: they are taken out afresh as the workers move.
            helper.getEntityTickets().keySet().forEach(helper::removeAllTickets);
            helper.getBlockTickets().forEach((owner, tickets) -> {
                if (manager.byBell(owner).isEmpty()) {
                    helper.removeAllTickets(owner);
                }
            });
        });
    }

    static void tick(ServerLevel level, VillageManager manager) {
        boolean on = LVConfig.KEEP_LOADED.get();
        int max = LVConfig.MAX_LOADED_VILLAGES.get();
        int cap = LVConfig.LOADED_CHUNK_RADIUS.get();
        // The biggest first; one already held keeps its place against one merely as big (no swapping on ties).
        boolean visitedOnly = LVConfig.LOAD_VISITED_ONLY.get();
        List<Village> chosen = on ? manager.all().stream()
                .filter(v -> !visitedOnly || v.data().getBoolean("visited"))
                .sorted(Comparator.comparingInt((Village v) -> v.population() * 2 + (FORCED.containsKey(v.id()) ? 1 : 0))
                        .reversed()).limit(max).toList() : List.of();
        int budget = NEW_PER_SCAN; // chunks newly taken on per scan: a few at a time, never a heap at once
        Set<UUID> keep = new HashSet<>();
        for (Village v : chosen) {
            keep.add(v.id());
            ChunkPos c = new ChunkPos(v.center());
            int r = Math.min(cap, (v.radius() + 15) / 16);
            Set<Long> want = new HashSet<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    want.add(ChunkPos.asLong(c.x + dx, c.z + dz));
                }
            }
            var had = FORCED.computeIfAbsent(v.id(), k -> Map.entry(v.center(), new HashSet<>()));
            Set<Long> held = new HashSet<>();
            // Nearest the bell first, so a village coming under the loader gets its heart going before its edges.
            List<Long> order = want.stream().sorted(Comparator.comparingInt((Long l) ->
                    Math.max(Math.abs(ChunkPos.getX(l) - c.x), Math.abs(ChunkPos.getZ(l) - c.z)))).toList();
            for (long l : order) {
                if (had.getValue().contains(l)) {
                    held.add(l);
                } else if (budget > 0 || level.hasChunk(ChunkPos.getX(l), ChunkPos.getZ(l))) {
                    // (one in memory already costs nothing to hold on to)
                    if (!level.hasChunk(ChunkPos.getX(l), ChunkPos.getZ(l))) {
                        budget--;
                    }
                    ForgeChunkManager.forceChunk(level, LivingVillages.MODID, v.center(), ChunkPos.getX(l),
                            ChunkPos.getZ(l), true, true);
                    held.add(l);
                }
            }
            for (long l : had.getValue()) {
                if (!want.contains(l)) {
                    ForgeChunkManager.forceChunk(level, LivingVillages.MODID, v.center(), ChunkPos.getX(l),
                            ChunkPos.getZ(l), false, true);
                }
            }
            had.getValue().clear();
            had.getValue().addAll(held);
        }
        for (UUID id : Set.copyOf(FORCED.keySet())) {
            if (!keep.contains(id)) {
                var had = FORCED.remove(id);
                for (long l : had.getValue()) {
                    ForgeChunkManager.forceChunk(level, LivingVillages.MODID, had.getKey(), ChunkPos.getX(l),
                            ChunkPos.getZ(l), false, true);
                }
            }
        }
    }

    public static void clear() {
        FORCED.clear();
        AWAY.clear();
    }

    /** Workers out past their village's loaded ground (a hunter after game, a miner deep in his tunnel) -> held chunk. */
    private static final Map<UUID, Long> AWAY = new HashMap<>();

    /**
     * Called by a worker now and then: out past the village's loaded ground, the chunks round him are held too,
     * or he would stop dead the moment he walked off it with nobody about, and stay that way for good.
     */
    public static void follow(ServerLevel level, net.minecraft.world.entity.Entity worker, Village v) {
        Long held = AWAY.get(worker.getUUID());
        ChunkPos at = new ChunkPos(worker.blockPosition());
        var forced = FORCED.get(v.id());
        // Near the edge already counts: one step over it and he could no longer ask.
        boolean away = false;
        for (int dx = -1; dx <= 1 && worker.isAlive() && forced != null && !away; dx++) {
            for (int dz = -1; dz <= 1 && !away; dz++) {
                away = !forced.getValue().contains(ChunkPos.asLong(at.x + dx, at.z + dz));
            }
        }
        if (away && held != null && held == at.toLong()) {
            return;
        }
        if (held != null) {
            hold(level, worker, new ChunkPos(held), false);
            AWAY.remove(worker.getUUID());
        }
        if (away) {
            hold(level, worker, at, true);
            AWAY.put(worker.getUUID(), at.toLong());
        }
    }

    /** A worker who died or retired: whatever was held round him is let go. */
    public static void release(ServerLevel level, net.minecraft.world.entity.Entity worker) {
        Long held = AWAY.remove(worker.getUUID());
        if (held != null) {
            hold(level, worker, new ChunkPos(held), false);
        }
    }

    private static void hold(ServerLevel level, net.minecraft.world.entity.Entity worker, ChunkPos c, boolean add) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ForgeChunkManager.forceChunk(level, LivingVillages.MODID, worker, c.x + dx, c.z + dz, add, true);
            }
        }
    }
}
