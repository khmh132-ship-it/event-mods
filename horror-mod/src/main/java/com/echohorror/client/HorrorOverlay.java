package com.echohorror.client;

import com.echohorror.Config;
import com.echohorror.EchoHorror;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/** Full-screen horror effects drawn above the HUD. */
public final class HorrorOverlay {
    private static final ResourceLocation VIGNETTE = new ResourceLocation(EchoHorror.MODID, "textures/gui/vignette.png");
    private static final ResourceLocation NOISE = new ResourceLocation(EchoHorror.MODID, "textures/gui/noise.png");
    private static final ResourceLocation[] FACES = {
            new ResourceLocation(EchoHorror.MODID, "textures/gui/face1.png"),
            new ResourceLocation(EchoHorror.MODID, "textures/gui/face2.png"),
            new ResourceLocation(EchoHorror.MODID, "textures/gui/face3.png")};
    private static final RandomSource R = RandomSource.create();

    private HorrorOverlay() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui && ClientState.jumpscare < 0) return;
        boolean effects = Config.SCREEN_EFFECTS.get();
        float s = ClientState.sanity;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        if (effects && ClientState.chapter > 0) {
            // vignette grows as sanity drops, pulses with the heartbeat
            float fear = 1f - s / 100f;
            float pulse = 0f;
            if (ClientState.heartbeat > 0 || s < 20) pulse = (float) Math.pow(Math.max(0, Math.sin(ClientState.time * (s < 10 ? 0.5 : 0.3))), 8) * 0.25f;
            float va = Mth.clamp(fear * 0.95f + (ClientState.deep() ? 0.2f : 0f) + pulse + ClientState.watched * 0.6f, 0f, 1f);
            if (va > 0.02f) {
                g.setColor(ClientState.echoNight() ? 0.35f : 0f, 0f, 0f, va);
                g.blit(VIGNETTE, 0, 0, w, h, 0, 0, 256, 256, 256, 256);
                g.setColor(1f, 1f, 1f, 1f);
            }
            // film grain
            if (s < 65) {
                float ga = (65 - s) / 65f * 0.22f;
                g.setColor(1f, 1f, 1f, ga);
                int ox = R.nextInt(128), oy = R.nextInt(128);
                for (int x = -ox; x < w; x += 128)
                    for (int y = -oy; y < h; y += 128) g.blit(NOISE, x, y, 0, 0, 128, 128, 128, 128);
                g.setColor(1f, 1f, 1f, 1f);
            }
            if (ClientState.echoNight()) g.fill(0, 0, w, h, 0x1A400000);
        }

        // glitch bars
        if (ClientState.glitch > 0) {
            for (int i = 0; i < 6 + R.nextInt(6); i++) {
                int y = R.nextInt(h), bh = 1 + R.nextInt(8);
                int col = (R.nextInt(3) == 0 ? 0x90FF0000 : R.nextInt(2) == 0 ? 0x9000FFFF : 0xB0000000);
                g.fill(0, y, w, y + bh, col);
            }
        }
        // flicker: the lights go out for a frame or two
        if (ClientState.flicker > 0 && R.nextFloat() < 0.35f) g.fill(0, 0, w, h, 0xE0000000);
        if (ClientState.blackout > 0) {
            int a = ClientState.blackout > 5 ? 255 : ClientState.blackout * 50;
            g.fill(0, 0, w, h, a << 24);
        }
        if (ClientState.whiteFlash > 0) {
            int a = (int) (200f * ClientState.whiteFlash / ClientState.whiteFlashMax);
            g.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
        }

        Font font = mc.font;
        // subliminal text
        if (ClientState.screenTextTicks > 0) {
            g.pose().pushPose();
            float scale = Math.max(2f, Math.min(6f, w / 90f));
            g.pose().translate(w / 2f + R.nextInt(5) - 2, h / 2f + R.nextInt(5) - 2, 0);
            g.pose().scale(scale, scale, 1);
            g.drawCenteredString(font, ClientState.screenText, 0, -4, 0xFFAA0000);
            g.pose().popPose();
        }

        // chapter title card
        if (ClientState.chapterTicks > 0) {
            int t = 160 - ClientState.chapterTicks;
            float a = t < 30 ? t / 30f : ClientState.chapterTicks < 40 ? ClientState.chapterTicks / 40f : 1f;
            int alpha = Mth.clamp((int) (a * 255), 4, 255);
            g.fill(0, h / 2 - 36, w, h / 2 + 30, (int) (a * 150) << 24);
            g.pose().pushPose();
            g.pose().translate(w / 2f, h / 2f - 22, 0);
            g.pose().scale(3f, 3f, 1);
            g.drawCenteredString(font, ClientState.chapterTitle, 0, 0, (alpha << 24) | 0xD8D8D8);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(w / 2f, h / 2f + 10, 0);
            g.pose().scale(1.5f, 1.5f, 1);
            g.drawCenteredString(font, ClientState.chapterSub, 0, 0, (alpha << 24) | 0x9A2020);
            g.pose().popPose();
        }

        // radio / tape subtitles
        if (!ClientState.subtitle.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.literal(ClientState.subtitle), Math.min(320, w - 40));
            int bh = lines.size() * 10 + 6;
            int y0 = h - 78 - bh;
            int maxW = 0;
            for (FormattedCharSequence l : lines) maxW = Math.max(maxW, font.width(l));
            g.fill(w / 2 - maxW / 2 - 6, y0 - 3, w / 2 + maxW / 2 + 6, y0 + bh - 3, 0x99000000);
            int y = y0;
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, w / 2 - font.width(l) / 2, y, 0xFFC8C8B4, false);
                y += 10;
            }
        }

        // jumpscare
        if (ClientState.jumpscare >= 0) {
            int t = ClientState.jumpscare;
            float zoom = 0.85f + Math.min(t, 8) * 0.06f;
            int size = (int) (Math.min(w, h) * zoom);
            int jx = R.nextInt(13) - 6, jy = R.nextInt(13) - 6;
            g.fill(0, 0, w, h, 0xFF000000);
            boolean calm = Config.REDUCE_FLASHING.get();
            if (calm) g.setColor(0.6f, 0.6f, 0.6f, 1f);
            g.blit(FACES[Mth.clamp(ClientState.jumpscareFace, 0, 2)], (w - size) / 2 + jx, (h - size) / 2 + jy, size, size, 0, 0, 256, 256, 256, 256);
            g.setColor(1f, 1f, 1f, 1f);
            if (!calm && t % 3 == 0) g.fill(0, 0, w, h, 0x50FF0000);
        }
        g.pose().popPose();
        RenderSystem.disableBlend();
    }
}
