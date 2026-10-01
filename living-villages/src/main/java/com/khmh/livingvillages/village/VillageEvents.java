package com.khmh.livingvillages.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovers villages around players (any bell with villagers nearby becomes one),
 * keeps loaded villages' population up to date and disbands villages whose bell is gone.
 */
public final class VillageEvents {
    private static final int SCAN_INTERVAL = 100;
    private static final int DISCOVERY_RADIUS = 64;

    private VillageEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % SCAN_INTERVAL != 0) {
            return;
        }
        VillageManager manager = VillageManager.get(level);
        discover(level, manager);
        refresh(level, manager);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getState().is(Blocks.BELL)) {
            VillageManager manager = VillageManager.get(level);
            manager.byBell(event.getPos()).ifPresent(manager::remove);
        }
    }

    private static void discover(ServerLevel level, VillageManager manager) {
        for (ServerPlayer player : level.players()) {
            List<BlockPos> bells = level.getPoiManager()
                    .findAll(type -> type.is(PoiTypes.MEETING), pos -> true,
                            player.blockPosition(), DISCOVERY_RADIUS, PoiManager.Occupancy.ANY)
                    .toList();
            for (BlockPos bell : bells) {
                // A bell inside an existing village is just a second bell of that village.
                if (manager.at(bell).isPresent()) {
                    continue;
                }
                int villagers = countVillagers(level, bell, Village.DEFAULT_RADIUS);
                if (villagers > 0) {
                    manager.create(bell).observe(villagers, level.getGameTime());
                }
            }
        }
    }

    private static void refresh(ServerLevel level, VillageManager manager) {
        List<Village> gone = new ArrayList<>();
        for (Village v : manager.all()) {
            if (!level.hasChunkAt(v.center())) {
                continue; // unloaded: keep last known state, abstract simulation will handle it
            }
            if (!level.getBlockState(v.center()).is(Blocks.BELL)) {
                gone.add(v); // bell removed by explosion, piston, /setblock...
                continue;
            }
            v.observe(countVillagers(level, v.center(), v.radius()), level.getGameTime());
        }
        gone.forEach(manager::remove);
    }

    private static int countVillagers(ServerLevel level, BlockPos center, int radius) {
        AABB box = new AABB(center).inflate(radius);
        return level.getEntitiesOfClass(Villager.class, box,
                v -> v.isAlive() && v.blockPosition().distSqr(center) <= (double) radius * radius).size();
    }
}
