package com.echohorror.horror;

import com.echohorror.entity.CrawlerEntity;
import com.echohorror.entity.MimicEntity;
import com.echohorror.entity.PhantomEntity;
import com.echohorror.entity.SilentEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** While Masha's music box plays, its carrier carries a little circle of quiet. */
public final class MusicBoxAura {
    public static final double RADIUS = 12;
    private static final Map<UUID, Long> ACTIVE = new HashMap<>(); // player -> game time when the tune ends

    private MusicBoxAura() {}

    public static void start(ServerPlayer p, int ticks) {
        ACTIVE.put(p.getUUID(), p.level().getGameTime() + ticks);
    }

    /** True if {@code p} stands inside someone's playing music box. */
    public static boolean protects(ServerPlayer p) {
        for (Map.Entry<UUID, Long> e : ACTIVE.entrySet()) {
            if (e.getValue() < p.level().getGameTime()) continue;
            ServerPlayer holder = p.server.getPlayerList().getPlayer(e.getKey());
            if (holder != null && holder.level() == p.level() && holder.distanceTo(p) < RADIUS) return true;
        }
        return false;
    }

    /** Every 10 ticks. */
    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Long>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || e.getValue() < p.level().getGameTime()) {
                it.remove();
                continue;
            }
            ServerLevel level = p.serverLevel();
            Vec3 c = p.position();
            AABB box = p.getBoundingBox().inflate(RADIUS + 6);
            if (level.getGameTime() % 20 == 0) {
                level.sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 1.4, p.getZ(), 1, 0.4, 0.2, 0.4, level.random.nextDouble());
                for (ServerPlayer o : level.players()) {
                    if (o.distanceTo(p) < RADIUS) Sanity.add(o, 1.5f);
                }
            }
            for (PhantomEntity ph : level.getEntitiesOfClass(PhantomEntity.class, box)) ph.dissolve();
            for (SilentEntity s : level.getEntitiesOfClass(SilentEntity.class, box)) s.freeze(30);
            for (Mob m : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof CrawlerEntity || m instanceof MimicEntity mi && mi.isRevealed())) {
                if (m.distanceToSqr(c) > (RADIUS + 4) * (RADIUS + 4)) continue;
                m.setTarget(null);
                Vec3 away = m.position().subtract(c).normalize().scale(RADIUS + 6).add(c);
                m.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
