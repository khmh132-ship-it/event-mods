package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

/**
 * The cartographer walks ever wider circles around the village and charts what he sees: woods, sand, clay and
 * sugar cane. The lumberjack goes for charted woods when the trees near his hut are gone, the librarian for
 * charted cane.
 */
public class CartographerGoal extends Goal {
    private static final int STEPS = 16;

    private final VillageWorker worker;
    private BlockPos waypoint;
    private int step;
    private int ring = 1;
    private int timer;

    public CartographerGoal(VillageWorker worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return worker.job() == WorkerJob.CARTOGRAPHER && worker.village().isPresent();
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
    public void tick() {
        Village village = worker.village().orElse(null);
        if (village == null || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        timer++;
        if (waypoint == null) {
            double angle = Math.PI * 2 * step / STEPS;
            int r = 24 * ring;
            BlockPos c = village.center();
            BlockPos p = c.offset((int) (Math.cos(angle) * r), 0, (int) (Math.sin(angle) * r));
            if (!level.hasChunkAt(p)) {
                nextWaypoint();
                return;
            }
            waypoint = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
            timer = 0;
        }
        worker.setStatus("charting ring " + ring + ", point " + (step + 1) + "/" + STEPS);
        if (worker.distanceToSqr(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5) > 9 && timer < 600) {
            if (timer % 20 == 1) {
                worker.getNavigation().moveTo(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5, 0.6);
            }
            return;
        }
        chart(level, village, worker.blockPosition());
        nextWaypoint();
    }

    private void nextWaypoint() {
        waypoint = null;
        step++;
        if (step >= STEPS) {
            step = 0;
            ring = ring >= 4 ? 1 : ring + 1;
        }
    }

    /** Looks around (16 blocks) and notes what is there. */
    private static void chart(ServerLevel level, Village village, BlockPos at) {
        for (int dx = -16; dx <= 16; dx += 2) {
            for (int dz = -16; dz <= 16; dz += 2) {
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.offset(dx, 0, dz)).below();
                BlockState s = level.getBlockState(top);
                if (s.is(BlockTags.LOGS)) {
                    while (level.getBlockState(top.below()).is(BlockTags.LOGS)) {
                        top = top.below();
                    }
                    Scouting.mark(village, "trees", top);
                } else if (s.is(Blocks.SUGAR_CANE)) {
                    while (level.getBlockState(top.below()).is(Blocks.SUGAR_CANE)) {
                        top = top.below();
                    }
                    Scouting.mark(village, "cane", top);
                } else if (s.is(BlockTags.SAND)) {
                    Scouting.mark(village, "sand", top);
                } else if (s.is(Blocks.CLAY)) {
                    Scouting.mark(village, "clay", top);
                }
            }
        }
    }
}
