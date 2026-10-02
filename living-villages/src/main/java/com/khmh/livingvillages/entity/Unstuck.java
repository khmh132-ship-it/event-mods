package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Getting out of a fix the way a player does, never by teleporting: a villager who has wanted to go somewhere
 * for a while without getting anywhere breaks what blocks his way (natural ground, leaves, never a village
 * building) or, when his goal is higher up, jumps and puts a block under his feet.
 */
final class Unstuck {
    private static final List<net.minecraft.world.item.Item> FILL = List.of(Items.COBBLESTONE, Items.DIRT,
            Items.COBBLED_DEEPSLATE, Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.NETHERRACK);

    private final VillageWorker worker;
    private Vec3 lastPos = Vec3.ZERO;
    private int still;
    @Nullable
    private BlockPos pillar;
    @Nullable
    private BlockPos breaking;
    private int breakTicks;

    Unstuck(VillageWorker worker) {
        this.worker = worker;
    }

    /** Called every tick on the server with where the worker last asked to walk (null if he is not walking). */
    void tick(ServerLevel level, @Nullable Vec3 wanted, long wantedAt) {
        if (pillar != null) {
            finishPillar(level);
            return;
        }
        if (wanted == null || level.getGameTime() - wantedAt > 60 || worker.isSleeping()
                || worker.position().distanceTo(wanted) < 2.0) {
            still = 0;
            resetBreak(level);
            return;
        }
        if (worker.tickCount % 20 == 0) {
            // Bobbing in water is no progress: only ground covered counts.
            double dx = worker.getX() - lastPos.x, dz = worker.getZ() - lastPos.z;
            still = dx * dx + dz * dz < 0.16 ? still + 1 : 0;
            lastPos = worker.position();
        }
        if (still < 5) { // five seconds without getting anywhere
            return;
        }
        BlockPos feet = worker.blockPosition();
        double dx = wanted.x - worker.getX(), dz = wanted.z - worker.getZ();
        Direction dir = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        boolean upward = wanted.y > feet.getY() + 1.5;
        // A wall in the way at head or foot height (or overhead when climbing): dig through it.
        BlockPos[] ahead = upward
                ? new BlockPos[]{feet.above(2), feet.relative(dir).above(), feet.relative(dir).above(2)}
                : new BlockPos[]{feet.relative(dir).above(), feet.relative(dir)};
        for (BlockPos p : ahead) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.getCollisionShape(level, p).isEmpty() == false) {
                if (breakable(level, p, s)) {
                    dig(level, p, s);
                    return;
                }
                if (!upward) {
                    break; // a village wall: no breaking that; try going over instead
                }
            }
        }
        // Climb: jump and put a block underfoot.
        // Out of water up a bank, too: the block goes where he swims.
        boolean footing = worker.onGround() || worker.isInWater();
        boolean upwardOrBank = upward || worker.isInWater() && wanted.y > worker.getY() + 0.5;
        if ((upwardOrBank || still > 10) && footing && level.getBlockState(feet.above(2)).isAir()
                && (level.getBlockState(feet).isAir() || level.getBlockState(feet).canBeReplaced())) {
            ItemStack fill = fill();
            if (fill != null && fill.getItem() instanceof BlockItem bi) {
                // Jump-and-place in one go: up a block, the block underfoot.
                worker.getNavigation().stop();
                worker.setPos(worker.getX(), feet.getY() + 1.0, worker.getZ());
                level.setBlock(feet, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
                fill.shrink(1);
                worker.getInventory().setChanged();
                worker.swing(InteractionHand.MAIN_HAND);
                still = 3;
            }
        }
    }

    private void finishPillar(ServerLevel level) {
        if (worker.getY() >= pillar.getY() + 0.9 && level.getBlockState(pillar).isAir()) {
            ItemStack fill = fill();
            if (fill != null && fill.getItem() instanceof BlockItem bi) {
                level.setBlock(pillar, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
                fill.shrink(1);
                worker.getInventory().setChanged();
                worker.swing(InteractionHand.MAIN_HAND);
            }
            pillar = null;
            still = 0;
        } else if (worker.onGround() && worker.getY() < pillar.getY() + 0.5 && worker.tickCount % 10 == 0) {
            pillar = null; // the jump did not come off
        }
    }

    private void dig(ServerLevel level, BlockPos p, BlockState s) {
        if (!p.equals(breaking)) {
            resetBreak(level);
            breaking = p;
        }
        worker.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        float hardness = s.getDestroySpeed(level, p);
        float speed = Math.max(1.0F, worker.getMainHandItem().getDestroySpeed(s));
        int need = Math.max(4, (int) (hardness * 30 / speed));
        breakTicks++;
        level.destroyBlockProgress(worker.getId(), p, Math.min(9, breakTicks * 10 / need));
        if (breakTicks % 5 == 0) {
            worker.swing(InteractionHand.MAIN_HAND);
        }
        if (breakTicks >= need) {
            level.destroyBlock(p, true, worker);
            resetBreak(level);
            still = 3; // try walking again before the next block
        }
    }

    private void resetBreak(ServerLevel level) {
        if (breaking != null) {
            level.destroyBlockProgress(worker.getId(), breaking, -1);
        }
        breaking = null;
        breakTicks = 0;
    }

    /** Natural ground, leaves, snow: yes. Anything of a village building, or unbreakable: no. */
    private boolean breakable(ServerLevel level, BlockPos p, BlockState s) {
        if (s.getDestroySpeed(level, p) < 0 || s.getDestroySpeed(level, p) > 20 || !s.getFluidState().isEmpty()) {
            return false;
        }
        boolean natural = com.khmh.livingvillages.building.SiteFinder.isNatural(s) || s.is(BlockTags.LEAVES)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL) || s.is(BlockTags.SNOW)
                || s.is(Blocks.COBBLESTONE) && !inBuilding(p);
        return natural && !inBuilding(p);
    }

    private boolean inBuilding(BlockPos p) {
        return worker.village().map(v -> v.buildings().stream().map(Building::box).anyMatch(b -> b.isInside(p)))
                .orElse(false);
    }

    @Nullable
    private ItemStack fill() {
        var inv = worker.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && (FILL.contains(s.getItem()) || s.is(net.minecraft.tags.ItemTags.LOGS))) {
                return s;
            }
        }
        return null;
    }
}
