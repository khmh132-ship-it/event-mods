package com.echohorror.client.screen;

import com.echohorror.EchoHorror;
import com.echohorror.client.ClientState;
import com.echohorror.story.Notes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public class NoteScreen extends Screen {
    private static final ResourceLocation BG = new ResourceLocation(EchoHorror.MODID, "textures/gui/note.png");
    private static final int W = 150, H = 180, LINES = 14;
    private final Notes.Note note;
    private final Screen parent;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private final RandomSource rnd = RandomSource.create();
    private int page;
    private int glitchLine = -1, glitchTicks;

    public NoteScreen(String id, Screen parent) {
        super(Component.literal("Записка"));
        this.note = Notes.get(id);
        this.parent = parent;
    }

    @Override
    protected void init() {
        lines.clear();
        if (note != null) {
            for (String para : personalize(note.text()).split("\n")) {
                if (para.isEmpty()) {
                    lines.add(FormattedCharSequence.EMPTY);
                    continue;
                }
                lines.addAll(font.split(Component.literal(para), W - 22));
            }
        }
        int l = (width - W) / 2, t = (height - H) / 2;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1)).bounds(l + 6, t + H + 4, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if ((page + 1) * LINES < lines.size()) page++;
        }).bounds(l + W - 26, t + H + 4, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose()).bounds(l + W / 2 - 30, t + H + 4, 60, 16).build());
    }

    /** Some notes know who is reading them. */
    static String personalize(String text) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (text.contains("{me}")) text = text.replace("{me}", mc.player != null ? mc.player.getGameProfile().getName() : "ты");
        if (text.contains("{players}")) {
            StringBuilder sb = new StringBuilder();
            if (mc.getConnection() != null) {
                for (net.minecraft.client.multiplayer.PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                    sb.append(pi.getProfile().getName()).append(" — ?? л.\n");
                }
            }
            text = text.replace("{players}", sb.length() == 0 ? "(неразборчиво)" : sb.toString().trim());
        }
        return text;
    }

    @Override
    public void tick() {
        if (glitchTicks > 0) glitchTicks--;
        else if (ClientState.sanity < 35 && rnd.nextFloat() < 0.02f) {
            glitchLine = rnd.nextInt(LINES);
            glitchTicks = 3 + rnd.nextInt(4);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = (width - W) / 2, t = (height - H) / 2;
        g.blit(BG, l, t, 0, 0, W, H, 150, 180);
        if (note == null) {
            g.drawCenteredString(font, "Лист пуст.", l + W / 2, t + 80, 0x404040);
        } else {
            List<FormattedCharSequence> title = font.split(Component.literal(note.title()), W - 20);
            int y = t + 9;
            for (FormattedCharSequence tl : title) {
                g.drawString(font, tl, l + 10, y, 0x6A1010, false);
                y += 9;
            }
            y += 4;
            for (int i = 0; i < LINES; i++) {
                int n = page * LINES + i;
                if (n >= lines.size()) break;
                if (glitchTicks > 0 && i == glitchLine) {
                    g.drawString(font, "ОБЕРНИСЬ ОБЕРНИСЬ ОБЕРНИСЬ", l + 10, y, 0xAA0000, false);
                } else {
                    g.drawString(font, lines.get(n), l + 10, y, 0x2B2622, false);
                }
                y += 10;
            }
            int pages = Math.max(1, (lines.size() + LINES - 1) / LINES);
            g.drawCenteredString(font, (page + 1) + "/" + pages, l + W / 2, t + H - 11, 0x504840);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
