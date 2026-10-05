package dev.khmh.trialcomplex.client;

import dev.khmh.trialcomplex.TrialComplex;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = TrialComplex.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("objective", (gui, g, pt, w, h) -> renderObjective(g, w));
        e.registerAboveAll("subtitle", (gui, g, pt, w, h) -> renderSubtitle(g, w, h));
    }

    private static void renderObjective(GuiGraphics g, int w) {
        var hud = ClientState.hud;
        Minecraft mc = Minecraft.getInstance();
        if (hud.index() < 0 || mc.options.hideGui) return;
        Font f = mc.font;
        long since = Util.getMillis() - ClientState.hudChanged;
        // свежая цель подсвечивается пару секунд
        int accent = since < 2500 && (since / 250) % 2 == 0 ? 0xFFFFE066 : 0xFFFFB000;
        String head = hud.total() > 0 ? "ИСПЫТАНИЕ " + hud.index() + "/" + hud.total() : "КОМПЛЕКС";
        int x = 6, y = 6;
        int boxW = Math.max(f.width(head), f.width(hud.title()));
        List<FormattedCharSequence> obj = hud.objective().isEmpty() ? List.of()
                : f.split(Component.literal(hud.objective()), 220);
        for (var line : obj) boxW = Math.max(boxW, f.width(line));
        int boxH = 22 + obj.size() * 10;
        g.fill(x - 3, y - 3, x + boxW + 3, y + boxH, 0x88000000);
        g.fill(x - 3, y - 3, x - 2, y + boxH, accent);
        g.drawString(f, head, x, y, accent, false);
        g.drawString(f, hud.title(), x, y + 10, 0xFFFFFFFF, false);
        int ly = y + 22;
        for (var line : obj) {
            g.drawString(f, line, x, ly, 0xFFBBBBBB, false);
            ly += 10;
        }
    }

    private static void renderSubtitle(GuiGraphics g, int w, int h) {
        long now = Util.getMillis();
        if (now > ClientState.subtitleUntil || ClientState.subtitle.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font f = mc.font;
        float alpha = Mth.clamp((ClientState.subtitleUntil - now) / 600f, 0f, 1f)
                * Mth.clamp((now - ClientState.subtitleStart) / 150f, 0f, 1f);
        if (alpha <= 0.02f) return;
        int a = (int) (alpha * 255) << 24;
        List<FormattedCharSequence> lines = f.split(Component.literal(ClientState.subtitle), Math.min(320, w - 40));
        int lineH = 10;
        int total = lines.size() * lineH + 12;
        int top = h - 72 - total;
        int maxW = f.width("ГОЛОС");
        for (var l : lines) maxW = Math.max(maxW, f.width(l));
        int cx = w / 2;
        g.fill(cx - maxW / 2 - 6, top - 4, cx + maxW / 2 + 6, top + total, ((int) (alpha * 0xAA)) << 24);
        g.drawCenteredString(f, Component.literal("ГОЛОС"), cx, top, 0xFF5555 | a);
        int y = top + 12;
        for (var l : lines) {
            g.drawString(f, l, cx - f.width(l) / 2, y, 0xFFFFFF | a, true);
            y += lineH;
        }
    }

    @Mod.EventBusSubscriber(modid = TrialComplex.MODID, value = Dist.CLIENT)
    public static final class Forge {
        private Forge() {}

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
            ClientState.onVoice("");
            ClientState.hud = new dev.khmh.trialcomplex.net.Net.Hud(-1, 0, "", "");
        }
    }
}
