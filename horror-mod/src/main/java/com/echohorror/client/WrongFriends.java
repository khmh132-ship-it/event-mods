package com.echohorror.client;

import com.echohorror.EchoHorror;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * At low sanity your friends look wrong — but only from the corner of your eye. Taller, thinner, head turned
 * towards you, your own name over their head. Look straight at them and they are fine again. Of course they are.
 */
@Mod.EventBusSubscriber(modid = EchoHorror.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WrongFriends {
    private static final class Saved {
        float headRot, headRotO, bodyRot, bodyRotO;
    }

    private static final Map<Integer, Saved> ACTIVE = new HashMap<>();

    private WrongFriends() {}

    /** 0 = normal, up to 1 = very wrong. */
    private static float wrongness(Player other, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || other == mc.player || other.isInvisible()) return 0;
        if (!com.echohorror.Config.SCREEN_EFFECTS.get()) return 0;
        float sanity = ClientState.sanity;
        if (sanity >= 40) return 0;
        // comes and goes in ~20 second windows, different for each friend
        long window = (ClientState.time / 400L) + other.getId() * 7L;
        if (Math.floorMod(window, 3) != 0 && sanity > 15) return 0;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 to = other.getPosition(partial).add(0, other.getBbHeight() * 0.7, 0).subtract(cam.getPosition());
        double dist = to.length();
        if (dist < 3 || dist > 48) return 0;
        Vec3 look = new Vec3(cam.getLookVector());
        double dot = look.dot(to.normalize());
        if (dot > 0.9) return 0; // looked at directly: perfectly normal
        if (dot < -0.1) return 0; // behind you, nothing to draw anyway
        return (40f - sanity) / 40f * (float) Mth.clamp((0.9 - dot) / 0.3, 0, 1);
    }

    // LOWEST: we must be the last to see Pre, so nobody cancels it after we pushed the pose (Post would never come)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPre(RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        if (e.isCanceled()) return;
        float w = wrongness(p, e.getPartialTick());
        if (w <= 0.05f) return;
        Saved s = new Saved();
        s.headRot = p.yHeadRot;
        s.headRotO = p.yHeadRotO;
        s.bodyRot = p.yBodyRot;
        s.bodyRotO = p.yBodyRotO;
        ACTIVE.put(p.getId(), s);
        // the head turns to you; the body doesn't
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = cam.x - p.getX(), dz = cam.z - p.getZ();
        float face = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        float head = Mth.rotLerp(Math.min(1f, w * 1.5f), s.headRot, face);
        p.yHeadRot = head;
        p.yHeadRotO = head;
        e.getPoseStack().pushPose();
        e.getPoseStack().scale(1f - 0.14f * w, 1f + 0.22f * w, 1f - 0.14f * w);
    }

    @SubscribeEvent
    public static void onPost(RenderPlayerEvent.Post e) {
        Saved s = ACTIVE.remove(e.getEntity().getId());
        if (s == null) return;
        e.getPoseStack().popPose();
        Player p = e.getEntity();
        p.yHeadRot = s.headRot;
        p.yHeadRotO = s.headRotO;
        p.yBodyRot = s.bodyRot;
        p.yBodyRotO = s.bodyRotO;
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent e) {
        if (!(e.getEntity() instanceof Player p) || !ACTIVE.containsKey(p.getId())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientState.sanity > 20) return;
        // whose name is that?
        e.setContent(Component.literal(mc.player.getGameProfile().getName()));
        e.setResult(Event.Result.ALLOW);
    }
}
