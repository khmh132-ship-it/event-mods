package com.echohorror.client.screen;

import com.echohorror.client.ClientState;
import com.echohorror.client.SoundSequencer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** A perfect copy of the "Connection Lost" screen. You are still connected. Something else is too. */
public class FakeDisconnectScreen extends Screen {
    private final Component reason = Component.translatable("disconnect.genericReason", "java.io.IOException: Эхо в канале связи");
    private int ticks;

    public FakeDisconnectScreen() {
        super(Component.translatable("disconnect.lost"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.toTitle"), b -> {
            ClientState.glitch = 4;
            minecraft.getSoundManager().play(SoundSequencer.flat("scare.glitch", 0.6f, false));
        }).bounds(width / 2 - 100, height / 2 + 40, 200, 20).build());
    }

    @Override
    public void tick() {
        if (++ticks > 110) {
            ClientState.glitch = 8;
            ClientState.blackout = 6;
            minecraft.getSoundManager().play(SoundSequencer.flat("scare.static", 0.8f, false));
            minecraft.setScreen(null);
            minecraft.gui.setOverlayMessage(Component.literal("Соединение восстановлено. Вы уверены, что это тот же сервер?")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderDirtBackground(g);
        List<FormattedCharSequence> lines = font.split(reason, width - 50);
        int y = height / 2 - lines.size() * 9 / 2;
        g.drawCenteredString(font, title, width / 2, y - 9 * 2, 0xAAAAAA);
        for (FormattedCharSequence l : lines) {
            g.drawCenteredString(font, l, width / 2, y, 0xFFFFFF);
            y += 9;
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
