package com.khmh.livingvillages.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Whether a villager can actually get his hand to a block: nothing solid in between, as for a player. */
public final class Reach {
    private Reach() {
    }

    public static boolean sees(Mob mob, BlockPos pos) {
        // Right beside it: in reach whatever the corners of the blocks round about (a low mine chamber).
        BlockPos at = mob.blockPosition();
        if (Math.abs(at.getX() - pos.getX()) <= 1 && Math.abs(at.getZ() - pos.getZ()) <= 1
                && pos.getY() >= at.getY() - 1 && pos.getY() <= at.getY() + 2) {
            return true;
        }
        Vec3 eye = mob.getEyePosition();
        Vec3 c = pos.getCenter();
        // The middle, the top and the side facing him: any of them in sight will do, as for a player.
        Vec3 toward = eye.subtract(c);
        Vec3 face = c.add(Math.signum(toward.x) * 0.45 * (Math.abs(toward.x) >= Math.abs(toward.z) ? 1 : 0), 0,
                Math.signum(toward.z) * 0.45 * (Math.abs(toward.z) > Math.abs(toward.x) ? 1 : 0));
        for (Vec3 aim : new Vec3[]{c, c.add(0, 0.45, 0), face}) {
            BlockHitResult hit = mob.level().clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, mob));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos)) {
                return true;
            }
        }
        return false;
    }
}
