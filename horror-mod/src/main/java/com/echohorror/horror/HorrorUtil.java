package com.echohorror.horror;

import com.echohorror.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HorrorUtil {
    private HorrorUtil() {}

    /** Plays a sound audible ONLY to the given player. */
    public static void playTo(ServerPlayer p, SoundEvent sound, Vec3 pos, float volume, float pitch) {
        Holder<SoundEvent> h = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        p.connection.send(new ClientboundSoundPacket(h, SoundSource.HOSTILE, pos.x, pos.y, pos.z, volume, pitch, p.getRandom().nextLong()));
    }

    public static void playTo(ServerPlayer p, String sound, Vec3 pos, float volume, float pitch) {
        playTo(p, ModSounds.get(sound), pos, volume, pitch);
    }

    /** Sound right at the player's head (non-directional feel). */
    public static void playAt(ServerPlayer p, String sound, float volume, float pitch) {
        playTo(p, ModSounds.get(sound), p.getEyePosition(), volume, pitch);
    }

    public static Vec3 horizontalLook(Player p) {
        Vec3 v = p.getLookAngle();
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
    }

    /** Position {@code dist} blocks behind the player at ear height. */
    public static Vec3 behind(Player p, double dist) {
        return p.getEyePosition().subtract(horizontalLook(p).scale(dist));
    }

    /** Direction rotated around Y by degrees. */
    public static Vec3 rotate(Vec3 dir, double degrees) {
        double r = Math.toRadians(degrees);
        double c = Math.cos(r), s = Math.sin(r);
        return new Vec3(dir.x * c - dir.z * s, 0, dir.x * s + dir.z * c);
    }

    public static boolean isLookingAt(Player p, Vec3 target, double minDot) {
        Vec3 to = target.subtract(p.getEyePosition());
        if (to.lengthSqr() < 1e-4) return true;
        return p.getViewVector(1f).dot(to.normalize()) > minDot;
    }

    public static boolean canSee(Player p, Vec3 target) {
        HitResult r = p.level().clip(new ClipContext(p.getEyePosition(), target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        return r.getType() == HitResult.Type.MISS || r.getLocation().distanceToSqr(target) < 1.0;
    }

    public static boolean isWatching(Player p, Entity e, double minDot) {
        Vec3 eye = e.position().add(0, e.getBbHeight() * 0.8, 0);
        return isLookingAt(p, eye, minDot) && canSee(p, eye);
    }

    /** True if the spot is dark enough for things to hide in. */
    public static boolean isDark(ServerLevel level, BlockPos pos) {
        return level.getMaxLocalRawBrightness(pos) <= 6;
    }

    public static boolean isUnderground(ServerPlayer p) {
        BlockPos pos = p.blockPosition();
        return !p.level().canSeeSky(pos) && p.level().getBrightness(LightLayer.SKY, pos) < 4;
    }

    public static boolean isNight(ServerLevel level) {
        long t = level.getDayTime() % 24000L;
        return t > 13000 && t < 23000;
    }

    public static boolean isMidnight(ServerLevel level) {
        long t = level.getDayTime() % 24000L;
        return t > 16800 && t < 20200;
    }

    /** Finds a standable position (feet) near x/z, searching vertically around baseY. */
    public static Optional<BlockPos> ground(ServerLevel level, int x, int z, int baseY, int up, int down) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = baseY + up; y >= baseY - down; y--) {
            m.set(x, y, z);
            BlockState below = level.getBlockState(m.below());
            if (below.isFaceSturdy(level, m.below(), net.minecraft.core.Direction.UP)
                    && level.getBlockState(m).getCollisionShape(level, m).isEmpty()
                    && level.getBlockState(m.above()).getCollisionShape(level, m.above()).isEmpty()
                    && level.getFluidState(m).isEmpty()) {
                return Optional.of(m.immutable());
            }
        }
        return Optional.empty();
    }

    /**
     * Random standable spot around the player at the given distance band. {@code angleCenter} is relative to the
     * player's horizontal look (0 = in front, 180 = behind), spread in degrees each side.
     */
    public static Optional<Vec3> spotAround(ServerPlayer p, double minD, double maxD, double angleCenter, double spread, boolean needDark) {
        ServerLevel level = p.serverLevel();
        RandomSource r = p.getRandom();
        Vec3 look = horizontalLook(p);
        for (int i = 0; i < 40; i++) {
            double sp = i < 24 ? spread : Math.min(180, spread * 2.5); // nothing there (water, cliff)? look wider
            double ang = angleCenter + (r.nextDouble() * 2 - 1) * sp;
            double d = minD + r.nextDouble() * (maxD - minD);
            Vec3 dir = rotate(look, ang);
            int x = Mth.floor(p.getX() + dir.x * d);
            int z = Mth.floor(p.getZ() + dir.z * d);
            if (!level.hasChunkAt(new BlockPos(x, p.getBlockY(), z))) continue;
            Optional<BlockPos> g = ground(level, x, z, p.getBlockY(), 6, 10);
            if (g.isEmpty()) continue;
            if (needDark && !isDark(level, g.get())) continue;
            return Optional.of(Vec3.atBottomCenterOf(g.get()));
        }
        return Optional.empty();
    }

    public static List<ServerPlayer> others(ServerPlayer p) {
        List<ServerPlayer> l = new ArrayList<>(p.server.getPlayerList().getPlayers());
        l.remove(p);
        return l;
    }

    public static boolean isSurvivalLike(Player p) {
        return !p.isSpectator() && !p.isCreative();
    }
}
