package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * The fisherman goes to the water's edge and fishes with a rod: casts, waits for a bite like a player does,
 * and reels in whatever the game's fishing table gives. The rod wears out. A full basket goes into the smoker
 * on the way to the warehouse.
 */
public class FishermanGoal extends Goal {
    private static final int LOAD = 16;

    private final VillageWorker worker;
    private final Tooling rod;
    private BlockPos spot;
    private BlockPos water;
    private int timer;
    private int bite;
    private boolean returning;

    public FishermanGoal(VillageWorker worker) {
        this.worker = worker;
        this.rod = new Tooling(worker);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return worker.job() == WorkerJob.FISHERMAN && worker.village().isPresent();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        rod.reset();
        worker.resetUnloading();
    }

    @Override
    public void tick() {
        Village village = worker.village().orElse(null);
        if (village == null || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        timer++;
        if (returning) {
            worker.setStatus("taking the catch in");
            if (worker.unloadTick()) {
                returning = false;
            }
            return;
        }
        if (rod.tick(level, village) != Tooling.Status.READY) {
            worker.setStatus("needs a fishing rod");
            return;
        }
        if (worker.carried() >= LOAD) {
            smoke(level, village);
            returning = true;
            return;
        }
        if (spot == null || timer % 2400 == 0) {
            findSpot(level, village);
            if (spot == null) {
                worker.setStatus("no water nearby");
                return;
            }
        }
        if (worker.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 2.5) {
            worker.setStatus("going fishing");
            if (timer % 20 == 1) {
                worker.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.6);
            }
            if (timer > 600) {
                spot = null;
                timer = 0;
            }
            return;
        }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(water.getX() + 0.5, water.getY() + 0.5, water.getZ() + 0.5);
        if (bite <= 0) {
            bite = 100 + level.random.nextInt(500); // like a bobber without Lure
            worker.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, worker.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F);
            worker.setStatus("fishing");
            return;
        }
        if (--bite == 0) {
            reelIn(level);
        }
    }

    private void reelIn(ServerLevel level) {
        ItemStack tool = worker.getMainHandItem();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(water))
                .withParameter(LootContextParams.TOOL, tool)
                .create(LootContextParamSets.FISHING);
        LootTable table = level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
        for (ItemStack s : table.getRandomItems(params)) {
            worker.carry(s.getItem(), s.getCount());
        }
        level.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.0F);
        worker.swing(InteractionHand.MAIN_HAND);
        rod.used();
    }

    private void smoke(ServerLevel level, Village village) {
        BlockPos smoker = Stations.find(level, village, com.khmh.livingvillages.economy.CraftPlanner.Station.FURNACE,
                worker.blockPosition());
        if (smoker != null && worker.distanceToSqr(smoker.getX() + 0.5, smoker.getY(), smoker.getZ() + 0.5) < 9) {
            FurnaceUse.serve(worker, level, smoker, List.of(Items.COD, Items.SALMON));
        }
    }

    /** A spot on land next to water, nearest to the bell, within the village and a little beyond. */
    private void findSpot(ServerLevel level, Village village) {
        BlockPos c = village.center();
        int r = village.radius() + 16;
        BlockPos best = null, bestWater = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c.offset(dx, 0, dz)).below();
                if (!level.hasChunkAt(top) || !level.getFluidState(top).is(FluidTags.WATER)) {
                    continue;
                }
                BlockPos land = landNextTo(level, top);
                if (land != null && land.distSqr(c) < bestDist) {
                    bestDist = land.distSqr(c);
                    best = land;
                    bestWater = top;
                }
            }
        }
        spot = best;
        water = bestWater;
        timer = 0;
    }

    @Nullable
    private static BlockPos landNextTo(ServerLevel level, BlockPos water) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = water.relative(d);
            for (int up = 0; up <= 1; up++) {
                BlockPos stand = p.above(up);
                if (level.getBlockState(stand.below()).isSolidRender(level, stand.below())
                        && level.getBlockState(stand).isAir() && level.getBlockState(stand.above()).isAir()) {
                    return stand;
                }
            }
        }
        return null;
    }
}
