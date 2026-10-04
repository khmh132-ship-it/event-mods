package com.echohorror.horror;

import com.echohorror.entity.CrawlerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The blind ones hunt by sound. Shots, explosions, breaking blocks, slamming doors and running feet carry;
 * a crouching player is silent.
 */
public final class NoiseSystem {
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Set<GameEvent> LOUD = Set.of(GameEvent.PROJECTILE_SHOOT, GameEvent.EXPLODE, GameEvent.BLOCK_DESTROY,
            GameEvent.BLOCK_PLACE, GameEvent.HIT_GROUND, GameEvent.ENTITY_DAMAGE, GameEvent.PRIME_FUSE, GameEvent.BLOCK_OPEN,
            GameEvent.BLOCK_CLOSE, GameEvent.CONTAINER_OPEN, GameEvent.CONTAINER_CLOSE, GameEvent.STEP, GameEvent.SPLASH,
            GameEvent.EAT, GameEvent.ITEM_INTERACT_FINISH);

    private NoiseSystem() {}

    /** A vanilla game event happened. */
    public static void onGameEvent(ServerLevel level, GameEvent ev, Vec3 pos, net.minecraft.world.entity.Entity cause) {
        if (!LOUD.contains(ev)) return;
        double radius;
        if (ev == GameEvent.EXPLODE || ev == GameEvent.PRIME_FUSE) radius = 40;
        else if (ev == GameEvent.PROJECTILE_SHOOT) radius = 28;
        else if (ev == GameEvent.STEP) {
            if (!(cause instanceof Player p) || !p.isSprinting()) return; // walking is fine, running is not
            radius = 12;
        } else radius = 14;
        if (cause instanceof Player p && p.isCrouching() && ev != GameEvent.EXPLODE && ev != GameEvent.PROJECTILE_SHOOT) radius *= 0.3;
        noise(level, pos, radius, cause instanceof ServerPlayer sp ? sp : null);
    }

    /** A loud sound was played on the server (guns from weapon mods usually end up here). */
    public static void onSound(ServerLevel level, Vec3 pos, float volume, SoundSource source) {
        if (volume < 2.0f || source == SoundSource.MUSIC || source == SoundSource.AMBIENT || source == SoundSource.HOSTILE) return;
        noise(level, pos, Math.min(64, 12 + volume * 6), null);
    }

    public static void noise(ServerLevel level, Vec3 pos, double radius, ServerPlayer source) {
        if (source != null) {
            long now = level.getGameTime();
            Long last = LAST.get(source.getUUID());
            if (last != null && now - last < 10) return;
            LAST.put(source.getUUID(), now);
        }
        AABB box = new AABB(pos, pos).inflate(radius);
        for (CrawlerEntity c : level.getEntitiesOfClass(CrawlerEntity.class, box)) {
            if (c.distanceToSqr(pos) > radius * radius) continue;
            c.hear(pos, source);
        }
    }
}
