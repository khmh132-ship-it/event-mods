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
        setMaxVisitedNodesMultiplier(4.0F); // looks harder for a way round before giving up
    }

    /** An ordinary walking pace, about that of a plain villager; the goals' own speeds only say who hurries. */
    private static double brisk(double speed) {
        return speed * 0.85;
    }

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        wanted = new Vec3(x, y, z);
        wantedAt = level.getGameTime();
        return super.moveTo(x, y, z, brisk(speed));
    }

    /** Stopping on purpose (to chop, to dig, to talk) is not being stuck. */
    @Override
    public boolean moveTo(@javax.annotation.Nullable net.minecraft.world.level.pathfinder.Path path, double speed) {
        return super.moveTo(path, brisk(speed));
    }

    /** True when there is no way to where he wants to go (no path, or one that stops short of it). */
    boolean noWay() {
        var p = getPath();
        return p == null || !p.canReach();
    }

    @Override
    public void stop() {
        super.stop();
        wanted = null;
    }

    @Override
    public boolean moveTo(Entity entity, double speed) {
        wanted = entity.position();
        wantedAt = level.getGameTime();
        return super.moveTo(entity, brisk(speed));
    }
}
