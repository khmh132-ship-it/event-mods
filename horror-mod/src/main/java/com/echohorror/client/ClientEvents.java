package com.echohorror.client;

import com.echohorror.EchoHorror;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EchoHorror.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    private static int heartbeatCd;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ClientState.tick();
        SoundSequencer.tick();
        ambience(mc);
        updateWatched(mc);
        if (mc.isPaused()) return;
        boolean beat = ClientState.heartbeat > 0 || (ClientState.chapter > 0 && ClientState.sanity < 15);
        if (beat && --heartbeatCd <= 0) {
            boolean fast = ClientState.sanity < 8 || ClientState.bossFight();
            heartbeatCd = fast ? 22 : 40;
            mc.getSoundManager().play(SoundSequencer.flat(fast ? "scare.heartbeat_fast" : "scare.heartbeat", 0.7f, false));
        }
    }

    private static AmbientLoop ambient;
    private static int tinnitusCd;

    /** Is one of the visions in your field of view? You may not have noticed it yet. Your body has. */
    private static void updateWatched(Minecraft mc) {
        float target = 0f;
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1f);
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof com.echohorror.entity.PhantomEntity)) continue;
            net.minecraft.world.phys.Vec3 to = e.position().add(0, 1.5, 0).subtract(eye);
            double dist = to.length();
            if (dist > 64 || dist < 0.5) continue;
            double dot = look.dot(to.normalize());
            if (dot > 0.75) target = Math.max(target, (float) ((dot - 0.75) / 0.25) * (float) Math.min(1.0, 24.0 / dist));
        }
        ClientState.watched += (Mth.clamp(target, 0f, 1f) - ClientState.watched) * 0.1f;
        if (ClientState.watched > 0.5f && --tinnitusCd <= 0) {
            tinnitusCd = 200;
            mc.getSoundManager().play(SoundSequencer.flat("scare.tinnitus", 0.25f, false));
        }
    }

    /** Chooses the background drone: the depths breathe, the night hums, the day is silent. */
    private static void ambience(Minecraft mc) {
        String want = null;
        if (ClientState.chapter > 0 && ClientState.chapter < 7 && !ClientState.bossFight() && mc.level != null) {
            long t = mc.level.getDayTime() % 24000L;
            boolean night = t > 13000 && t < 23000;
            boolean under = !mc.level.canSeeSky(mc.player.blockPosition()) && mc.player.getY() < mc.level.getSeaLevel() - 6;
            if (ClientState.deep()) want = "ambient.depths";
            else if (night || under || ClientState.sanity < 40) want = "ambient.drone";
        }
        if (ambient != null && (want == null || !want.equals(ambient.name())) && !ambient.isFadingOut()) ambient.fadeOut();
        if (ambient != null && (ambient.isStopped() || !mc.getSoundManager().isActive(ambient))) ambient = null;
        if (want != null && (ambient == null || ambient.isFadingOut() && ambient.isStopped())) {
            if (ambient == null) {
                ambient = new AmbientLoop(want, want.equals("ambient.depths") ? 0.55f : 0.35f);
                mc.getSoundManager().play(ambient);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientState.reset();
        SoundSequencer.reset();
        if (ambient != null) Minecraft.getInstance().getSoundManager().stop(ambient);
        ambient = null;
    }

    private static float targetFog() {
        float d = Float.MAX_VALUE;
        if (ClientState.fog > 0) d = Math.min(d, ClientState.fogDistance);
        if (ClientState.echoNight()) d = Math.min(d, 38f);
        if (ClientState.chapter > 0 && ClientState.sanity < 35) d = Math.min(d, Mth.lerp(ClientState.sanity / 35f, 18f, 64f));
        return d;
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog e) {
        if (e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        float d = targetFog();
        if (d == Float.MAX_VALUE || d >= e.getFarPlaneDistance()) return;
        e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), d * 0.15f));
        e.setFarPlaneDistance(d);
        e.setFogShape(FogShape.SPHERE);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor e) {
        if (ClientState.echoNight()) {
            e.setRed(0.16f);
            e.setGreen(0.02f);
            e.setBlue(0.02f);
        } else if (ClientState.fog > 0 || (ClientState.chapter > 0 && ClientState.sanity < 35)) {
            float k = 0.35f;
            e.setRed(e.getRed() * k);
            e.setGreen(e.getGreen() * k);
            e.setBlue(e.getBlue() * k);
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        float t = (float) (ClientState.time + e.getPartialTick());
        if (ClientState.shake > 0) {
            float s = ClientState.shakeStrength * Math.min(1f, ClientState.shake / 10f);
            e.setYaw(e.getYaw() + (float) Math.sin(t * 2.7f) * s);
            e.setPitch(e.getPitch() + (float) Math.cos(t * 3.1f) * s);
            e.setRoll(e.getRoll() + (float) Math.sin(t * 1.9f) * s * 1.5f);
        }
        if (ClientState.chapter > 0 && ClientState.sanity < 25) {
            float sway = (25 - ClientState.sanity) / 25f * 2.2f;
            e.setRoll(e.getRoll() + (float) Math.sin(t * 0.045f) * sway);
        }
    }
}
