package com.khmh.livingvillages.village;

import com.khmh.livingvillages.building.Construction;
import com.khmh.livingvillages.building.TemplateData;
import com.khmh.livingvillages.economy.CraftPlanner;
import com.khmh.livingvillages.entity.VillageWorker;
import com.khmh.livingvillages.stock.Stockpile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives villages: discovers them around players (any bell with villagers nearby becomes one), keeps loaded
 * villages' counts up to date, disbands villages whose bell is gone, and once a second runs production,
 * construction and growth planning.
 */
public final class VillageEvents {
    private static final int SCAN_INTERVAL = 100;
    private static final int DISCOVERY_RADIUS = 64;

    private VillageEvents() {
    }

    private static boolean extraTicks;

    /** Test speed-up: after each real tick, run the rest of the server's ticks straight away. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        int speed = com.khmh.livingvillages.config.LVConfig.TEST_SPEED.get();
        if (event.phase != TickEvent.Phase.END || speed <= 1 || extraTicks) {
            return;
        }
        extraTicks = true;
        try {
            for (int i = 1; i < speed; i++) {
                event.getServer().tickServer(() -> false);
            }
        } finally {
            extraTicks = false;
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now % 20 != 0) {
            return;
        }
        VillageManager manager = VillageManager.get(level);
        if (now % SCAN_INTERVAL == 0) {
            discover(level, manager);
            refresh(level, manager);
        }
        if (now % 400 == 0) {
            Founding.tick(level, manager);
        }
        if (now % 200 == 0 && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            VillageLoader.tick(level, manager);
        }
        for (Village v : List.copyOf(manager.all())) {
            boolean loaded = level.hasChunkAt(v.center());
            if (loaded) {
                checkAlarm(level, v);
                if (now % 100 == 0) {
                    QuestBoard.refresh(level, v);
                }
            }
            Production.tick(v, now, loaded);
            Construction.tick(level, v, now);
            if (loaded) {
                GrowthPlanner.tick(level, v, now);
            }
        }
    }

    /** A raid, or a pack of monsters at night, rings the bell: guards turn out, everyone else takes cover. */
    private static void checkAlarm(ServerLevel level, Village v) {
        boolean raid = level.getRaidAt(v.center()) != null;
        int monsters = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                // Only what is about the houses: not the cave spiders and zombies in the caves underneath.
                new AABB(v.center()).inflate(v.radius(), 0, v.radius()).expandTowards(0, 16, 0).expandTowards(0, -8, 0),
                m -> m.isAlive() && level.canSeeSky(m.blockPosition())).size();
        boolean alarm = raid || monsters >= 3;
        if (alarm && !v.alarm()) {
            var state = level.getBlockState(v.center());
            if (state.getBlock() instanceof net.minecraft.world.level.block.BellBlock bell) {
                bell.attemptToRing(level, v.center(), null);
            }
        }
        v.setAlarm(alarm);
    }

    @SubscribeEvent
    public static void onChunkLoad(net.minecraftforge.event.level.ChunkEvent.Load event) {
        if (event.isNewChunk() && event.getLevel() instanceof ServerLevel level) {
            Founding.onNewChunk(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getState().is(Blocks.BELL)) {
            VillageManager manager = VillageManager.get(level);
            manager.byBell(event.getPos()).ifPresent(manager::remove);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TemplateData.clearCache();
        VillageLoader.clear();
        CraftPlanner.clearCache();
    }

    private static void discover(ServerLevel level, VillageManager manager) {
        for (ServerPlayer player : level.players()) {
            discoverAround(level, manager, player.blockPosition(), DISCOVERY_RADIUS);
        }
    }

    /** Turns every bell near {@code pos} that has villagers around it into a village. Returns how many. */
    public static int discoverAround(ServerLevel level, VillageManager manager, BlockPos pos, int radius) {
        List<BlockPos> bells = level.getPoiManager()
                .findAll(type -> type.is(PoiTypes.MEETING), p -> true, pos, radius, PoiManager.Occupancy.ANY)
                .toList();
        int found = 0;
        for (BlockPos bell : bells) {
            // A bell inside an existing village is just a second bell of that village.
            if (manager.at(bell).isPresent()) {
                continue;
            }
            if (!villagers(level, bell, Village.DEFAULT_RADIUS).isEmpty()) {
                observe(level, manager.create(bell));
                found++;
            }
        }
        return found;
    }

    private static void refresh(ServerLevel level, VillageManager manager) {
        List<Village> gone = new ArrayList<>();
        for (Village v : manager.all()) {
            if (!level.hasChunkAt(v.center())) {
                continue; // unloaded: keep last known state, the abstract simulation goes on
            }
            if (!level.getBlockState(v.center()).is(Blocks.BELL)) {
                gone.add(v); // bell removed by explosion, piston, /setblock...
                continue;
            }
            observe(level, v);
        }
        gone.forEach(manager::remove);
    }

    static void observe(ServerLevel level, Village v) {
        List<Villager> villagers = villagers(level, v.center(), v.radius());
        Map<String, Integer> professions = new HashMap<>();
        for (Villager villager : villagers) {
            String key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession())
                    .getPath();
            professions.merge(key, 1, Integer::sum);
        }
        List<VillageWorker> workers = Workers.of(level, v);
        for (VillageWorker w : workers) {
            professions.merge(w.job().name().toLowerCase(), 1, Integer::sum);
        }
        int beds = (int) level.getPoiManager().getCountInRange(type -> type.is(PoiTypes.HOME), v.center(),
                v.radius(), PoiManager.Occupancy.ANY);
        v.observe(villagers.size() + workers.size(), beds, professions, level.getGameTime());
        Gathering.collectHarvest(v, villagers);
        Stockpile stock = v.stock(level);
        stock.flushBuffer();
        v.dropFinishedRequests(level.getGameTime());
        v.setStockFill(stock.fill());
        Births.tick(level, v, stock);
        Workers.hire(level, v, villagers, workers);
    }

    private static List<Villager> villagers(ServerLevel level, BlockPos center, int radius) {
        AABB box = new AABB(center).inflate(radius);
        return level.getEntitiesOfClass(Villager.class, box,
                v -> v.isAlive() && v.blockPosition().distSqr(center) <= (double) radius * radius);
    }
}
