package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * Between orders the librarian cuts sugar cane (for paper), always leaving the bottom piece to grow back, and
 * takes it to the warehouse. Cane the cartographer has seen further out counts too.
 */
public class SugarCaneGoal extends Goal {
    private final VillageWorker worker;
    private BlockPos target;
    private int timer;
    private int cooldown;

    public SugarCaneGoal(VillageWorker worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (worker.job() != WorkerJob.LIBRARIAN || --cooldown > 0) {
            return false;
        }
        cooldown = 200;
        Village v = worker.village().orElse(null);
        if (v == null || !(worker.level() instanceof ServerLevel level)) {
            return false;
        }
        if (worker.carried() >= 32) {
            target = null;
            return true;
        }
        BlockPos c = v.center();
        int r = v.radius();
        target = BlockPos.betweenClosedStream(c.getX() - r, c.getY() - 6, c.getZ() - r, c.getX() + r, c.getY() + 8, c.getZ() + r)
                .filter(p -> level.getBlockState(p).is(Blocks.SUGAR_CANE) && level.getBlockState(p.below()).is(Blocks.SUGAR_CANE))
                .map(BlockPos::immutable)
                .min(Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition())))
                .orElseGet(() -> Scouting.nearest(v, "cane", worker.blockPosition(), level,
                        p -> level.getBlockState(p.above()).is(Blocks.SUGAR_CANE)));
        if (target != null && level.getBlockState(target).is(Blocks.SUGAR_CANE)
                && !level.getBlockState(target.below()).is(Blocks.SUGAR_CANE)) {
            target = target.above(); // scouted base: cut from the second piece up
        }
        return target != null || worker.carried() > 0;
    }

    @Override
    public boolean canContinueToUse() {
        return timer < 900 && (target != null || worker.carried() > 0);
    }

    @Override
    public void start() {
        timer = 0;
    }

    @Override
    public void stop() {
        worker.resetUnloading();
        target = null;
    }

    @Override
    public void tick() {
        timer++;
        if (target == null) {
            worker.setStatus("taking cane in");
            if (worker.unloadTick()) {
                timer = 900;
            }
            return;
        }
        worker.setStatus("cutting sugar cane");
        if (worker.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) > 9) {
            if (timer % 20 == 1) {
                worker.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.6);
            }
            return;
        }
        if (worker.level().getBlockState(target).is(Blocks.SUGAR_CANE)) {
            int n = 0;
            BlockPos p = target;
            while (worker.level().getBlockState(p.above()).is(Blocks.SUGAR_CANE)) {
                p = p.above();
            }
            while (!p.equals(target.below())) {
                worker.level().destroyBlock(p, false, worker);
                n++;
                p = p.below();
            }
            worker.carry(Items.SUGAR_CANE, n);
            worker.swing(InteractionHand.MAIN_HAND);
        }
        target = null;
    }
}
