package com.echohorror.client.screen;

import com.echohorror.EchoHorror;
import com.echohorror.client.ClientState;
import com.echohorror.horror.Sanity;
import com.echohorror.story.Notes;
import com.echohorror.story.StoryManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class JournalScreen extends Screen {
    private static final ResourceLocation BG = new ResourceLocation(EchoHorror.MODID, "textures/gui/journal.png");
    private static final int W = 256, H = 180, PER_PAGE = 11;
    private int page;

    public JournalScreen() {
        super(Component.literal("Полевой дневник"));
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        int l = left(), t = top();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1)).bounds(l + 140, t + H - 22, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if ((page + 1) * PER_PAGE < ClientState.notes.size()) page++;
        }).bounds(l + 222, t + H - 22, 20, 16).build());
        List<String> notes = ClientState.notes;
        for (int i = 0; i < PER_PAGE; i++) {
            final int idx = i;
            addRenderableWidget(new Button(l + 136, t + 26 + i * 12, 112, 11, Component.empty(), b -> {
                int n = page * PER_PAGE + idx;
                if (n < ClientState.notes.size()) minecraft.setScreen(new NoteScreen(ClientState.notes.get(n), this));
            }, s -> Component.empty()) {
                @Override
                public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    int n = page * PER_PAGE + idx;
                    if (n >= ClientState.notes.size()) return;
                    Notes.Note note = Notes.get(ClientState.notes.get(n));
                    String title = note == null ? "???" : note.title();
                    if (font.width(title) > 110) title = font.plainSubstrByWidth(title, 104) + "…";
                    g.drawString(font, title, getX() + 2, getY() + 2, isHovered() ? 0x8A1010 : 0x2A2420, false);
                }
            });
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = left(), t = top();
        g.blit(BG, l, t, 0, 0, W, H, 256, 180);
        String[] title = StoryManager.chapterTitle(ClientState.chapter).split("\\|");
        g.drawString(font, title.length > 0 ? title[0] : "", l + 12, t + 10, 0x5A1010, false);
        if (title.length > 1) g.drawString(font, title[1], l + 12, t + 20, 0x3A3430, false);
        g.drawString(font, "Цель:", l + 12, t + 36, 0x5A1010, false);
        int y = t + 47;
        String objective = ClientState.objective;
        if (ClientState.sanity < 30 && (ClientState.time / 4) % 23 == 0) objective = "НЕ ДОВЕРЯЙ ИМ. ОНИ НЕ ТЕ, ЗА КОГО СЕБЯ ВЫДАЮТ. ОБЕРНИСЬ.";
        for (FormattedCharSequence line : font.split(Component.literal(objective), 112)) {
            if (y > t + 128) break;
            g.drawString(font, line, l + 12, y, 0x2A2420, false);
            y += 9;
        }
        g.drawString(font, "Состояние:", l + 12, t + 134, 0x5A1010, false);
        y = t + 144;
        for (FormattedCharSequence line : font.split(Component.literal(Sanity.describe(ClientState.sanity)), 112)) {
            g.drawString(font, line, l + 12, y, 0x4A3A34, false);
            y += 9;
        }
        g.drawString(font, "Найденные записи (" + ClientState.notes.size() + ")", l + 138, t + 12, 0x5A1010, false);
        int pages = Math.max(1, (ClientState.notes.size() + PER_PAGE - 1) / PER_PAGE);
        g.drawCenteredString(font, (page + 1) + "/" + pages, l + 191, t + H - 18, 0x3A3430);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
