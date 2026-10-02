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
        Vec3 eye = mob.getEyePosition();
        BlockHitResult hit = mob.level().clip(new ClipContext(eye, pos.getCenter(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mob));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
}
