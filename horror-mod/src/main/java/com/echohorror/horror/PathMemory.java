package com.echohorror.horror;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Remembers where every player walked and where they looked for the last ~5 minutes. The Echo replays it. */
public final class PathMemory {
    /** One sample every 2 ticks. */
    public record Sample(double x, double y, double z, float yaw, float pitch, boolean crouch, String dim) {
        public Vec3 pos() {
            return new Vec3(x, y, z);
        }
    }

    public static final int CAPACITY = 3000;
    private static final Map<UUID, ArrayDeque<Sample>> PATHS = new HashMap<>();

    private PathMemory() {}

    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            ArrayDeque<Sample> q = PATHS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
            String dim = p.level().dimension().location().toString();
            Sample last = q.peekLast();
            if (last != null && !last.dim().equals(dim)) q.clear();
            if (last != null && last.pos().distanceToSqr(p.position()) > 64) q.clear(); // teleported: the trail breaks
            q.addLast(new Sample(p.getX(), p.getY(), p.getZ(), p.getYHeadRot(), p.getXRot(), p.isCrouching(), dim));
            while (q.size() > CAPACITY) q.removeFirst();
        }
    }

    /** Samples, oldest first. */
    public static List<Sample> of(UUID id) {
        ArrayDeque<Sample> q = PATHS.get(id);
        return q == null ? List.of() : new ArrayList<>(q);
    }

    public static void forget(UUID id) {
        PATHS.remove(id);
    }

    public static void clear() {
        PATHS.clear();
    }
}
