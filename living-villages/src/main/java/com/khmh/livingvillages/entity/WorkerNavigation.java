package com.khmh.livingvillages.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Ground navigation that remembers where the worker last wanted to go, so being stuck can be noticed. */
class WorkerNavigation extends GroundPathNavigation {
    Vec3 wanted;
    long wantedAt;

    WorkerNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        wanted = new Vec3(x, y, z);
        wantedAt = level.getGameTime();
        return super.moveTo(x, y, z, speed);
    }

    /** Stopping on purpose (to chop, to dig, to talk) is not being stuck. */
    @Override
    public void stop() {
        super.stop();
        wanted = null;
    }

    @Override
    public boolean moveTo(Entity entity, double speed) {
        wanted = entity.position();
        wantedAt = level.getGameTime();
        return super.moveTo(entity, speed);
    }
}
