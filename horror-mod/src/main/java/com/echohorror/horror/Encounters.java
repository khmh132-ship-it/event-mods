package com.echohorror.horror;

import com.echohorror.Config;
import com.echohorror.network.Fx;
import com.echohorror.network.Net;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The moment a creature notices you, and the moment it gets you. Loud, and not too often. */
public final class Encounters {
    public static final int FACE_SHADE = 0, FACE_HOLES = 1, FACE_MIMIC = 2, FACE_SILENT = 3;
    private static final Map<UUID, Long> SPOTTED = new HashMap<>();
    private static final Map<UUID, Long> STRUCK = new HashMap<>();

    private Encounters() {}

    /** A creature has just picked you as its target. */
    public static void spotted(Mob m, ServerPlayer p) {
        long now = p.level().getGameTime();
        Long last = SPOTTED.get(p.getUUID());
        if (last != null && now - last < 400) return;
        SPOTTED.put(p.getUUID(), now);
        HorrorUtil.playTo(p, "scare.spotted", m.position().add(0, 1, 0), 2.2f, 0.9f + p.getRandom().nextFloat() * 0.2f);
        Net.fx(p, Fx.SHAKE, 14, 1.2f, "");
        Net.fx(p, Fx.HEARTBEAT, 160);
        Sanity.add(p, -4);
    }

    /** It touched you. Full-screen face and a scream, at most every 45 seconds. */
    public static void struck(Mob m, ServerPlayer p, int face) {
        long now = p.level().getGameTime();
        Long last = STRUCK.get(p.getUUID());
        if (last != null && now - last < 900) return;
        STRUCK.put(p.getUUID(), now);
        HorrorUtil.playAt(p, "scare.jumpscare", 2.5f, 1f);
        if (Config.JUMPSCARES.get() && !p.isPassenger() && !p.isFallFlying()) Net.fx(p, Fx.JUMPSCARE, face);
        else Net.fx(p, Fx.SHAKE, 25, 2f, "");
        Sanity.add(p, -8);
    }

    public static void forget(UUID id) {
        SPOTTED.remove(id);
        STRUCK.remove(id);
    }
}
