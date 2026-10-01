package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.UUID;

/**
 * Who sleeps where. A worker whose workplace has a bed gets that one (the house belongs to the job);
 * everybody else gets a free bed elsewhere in the village. Claims are kept with the village.
 */
final class Beds {
    private Beds() {
    }

    private static CompoundTag claims(Village village) {
        if (!village.data().contains("beds")) {
            village.data().put("beds", new CompoundTag());
        }
        return village.data().getCompound("beds");
    }

    /** The worker's bed (head part), claiming one if he has none; null if the village has no free bed. */
    @Nullable
    static BlockPos of(ServerLevel level, Village village, VillageWorker worker) {
        CompoundTag claims = claims(village);
        BlockPos own = worker.bed();
        if (own != null && isBed(level, own) && worker.getUUID().equals(owner(claims, own))) {
            return own;
        }
        BlockPos pick = null;
        Building home = worker.workplace().orElse(null);
        if (home != null) {
            pick = freeIn(level, claims, home.box(), worker);
        }
        if (pick == null) {
            pick = level.getPoiManager()
                    .findAll(t -> t.is(PoiTypes.HOME), p -> isFree(level, claims, p, worker), village.center(),
                            village.radius(), PoiManager.Occupancy.ANY)
                    .min(Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition()))).orElse(null);
        }
        if (pick == null) {
            return null;
        }
        if (own != null) {
            claims.remove(Long.toString(own.asLong()));
        }
        claims.putUUID(Long.toString(pick.asLong()), worker.getUUID());
        worker.setBed(pick);
        village.markDirty();
        return pick;
    }

    static void release(Village village, VillageWorker worker) {
        BlockPos own = worker.bed();
        if (own != null) {
            claims(village).remove(Long.toString(own.asLong()));
            village.markDirty();
        }
    }

    @Nullable
    private static BlockPos freeIn(ServerLevel level, CompoundTag claims, BoundingBox box, VillageWorker worker) {
        return BlockPos.betweenClosedStream(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                .filter(p -> isFree(level, claims, p, worker)).map(BlockPos::immutable).findFirst().orElse(null);
    }

    private static boolean isFree(ServerLevel level, CompoundTag claims, BlockPos p, VillageWorker worker) {
        if (!isBed(level, p)) {
            return false;
        }
        UUID owner = owner(claims, p);
        return (owner == null || owner.equals(worker.getUUID())) && !level.getBlockState(p).getValue(BedBlock.OCCUPIED);
    }

    private static boolean isBed(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    @Nullable
    private static UUID owner(CompoundTag claims, BlockPos p) {
        String key = Long.toString(p.asLong());
        return claims.hasUUID(key) ? claims.getUUID(key) : null;
    }
}
