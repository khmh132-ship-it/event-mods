package com.echohorror.client.screen;

import com.echohorror.client.ClientState;
import com.echohorror.client.SoundSequencer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Looks exactly like the vanilla death screen. It is not. */
public class FakeDeathScreen extends Screen {
    private final Component cause;
    private int ticks;

    public FakeDeathScreen(String cause) {
        super(Component.translatable("deathScreen.title"));
        this.cause = Component.literal(cause);
    }

    @Override
    protected void init() {
        Button respawn = Button.builder(Component.translatable("deathScreen.respawn"), b -> {}).bounds(width / 2 - 100, height / 4 + 72, 200, 20).build();
        Button title = Button.builder(Component.translatable("deathScreen.titleScreen"), b -> {}).bounds(width / 2 - 100, height / 4 + 96, 200, 20).build();
        respawn.active = false;
        title.active = false;
        addRenderableWidget(respawn);
        addRenderableWidget(title);
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks == 20) children().forEach(c -> {
            if (c instanceof Button b) b.active = true;
        });
        if (ticks > 80) {
            ClientState.glitch = 6;
            minecraft.getSoundManager().play(SoundSequencer.flat("scare.glitch", 0.8f, false));
            minecraft.setScreen(null);
            minecraft.gui.setOverlayMessage(Component.literal("Нет. Ещё рано.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x60500000, 0xA0803030);
        g.pose().pushPose();
        g.pose().scale(2.0F, 2.0F, 2.0F);
        g.drawCenteredString(font, title, width / 2 / 2, 30, 0xFFFFFF);
        g.pose().popPose();
        g.drawCenteredString(font, cause, width / 2, 85, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("deathScreen.score").append(": ")
                .append(Component.literal("0").withStyle(ChatFormatting.YELLOW)), width / 2, 100, 0xFFFFFF);
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
