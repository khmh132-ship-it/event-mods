package com.echohorror.client;

import com.echohorror.Config;
import com.echohorror.client.screen.*;
import com.echohorror.network.*;
import net.minecraft.client.Minecraft;

/** Packet handlers on the client thread. */
public final class ClientHandlers {
    private ClientHandlers() {}

    public static void handleState(StatePacket p) {
        ClientState.sanity = p.sanity();
        ClientState.chapter = p.chapter();
        ClientState.flags = p.flags();
    }

    public static void handleJournal(JournalPacket p) {
        ClientState.chapter = p.chapter();
        ClientState.objective = p.objective();
        ClientState.notes = p.notes();
        ClientState.places = p.places();
        if (p.open()) Minecraft.getInstance().setScreen(new JournalScreen());
    }

    public static void handleSequence(SoundSeqPacket p) {
        SoundSequencer.play(p);
    }

    public static void handleFx(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        boolean calm = Config.REDUCE_FLASHING.get();
        switch (p.type()) {
            case Fx.JUMPSCARE -> {
                if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
                ClientState.jumpscare = calm ? 8 : 0;
                ClientState.jumpscareFace = p.arg();
                ClientState.shake = 20;
                ClientState.shakeStrength = calm ? 0.5f : 2.5f;
            }
            case Fx.FAKE_DISCONNECT -> {
                if (mc.screen == null) mc.setScreen(new FakeDisconnectScreen());
            }
            case Fx.FAKE_DEATH -> {
                if (mc.screen == null) mc.setScreen(new FakeDeathScreen(p.text()));
            }
            case Fx.SCREEN_TEXT -> {
                ClientState.screenText = p.text();
                ClientState.screenTextTicks = Math.max(2, p.arg());
            }
            case Fx.SHAKE -> {
                ClientState.shake = p.arg();
                ClientState.shakeStrength = calm ? p.f() * 0.3f : p.f();
            }
            case Fx.BLACKOUT -> ClientState.blackout = p.arg();
            case Fx.FLICKER -> ClientState.flicker = calm ? 0 : p.arg();
            case Fx.GLITCH -> ClientState.glitch = calm ? Math.min(3, p.arg()) : p.arg();
            case Fx.CREDITS -> mc.setScreen(new CreditsScreen(p.arg() == 1));
            case Fx.CHAPTER -> {
                String[] parts = p.text().split("\\|", 2);
                ClientState.chapterTitle = parts[0];
                ClientState.chapterSub = parts.length > 1 ? parts[1] : "";
                ClientState.chapterTicks = 160;
            }
            case Fx.FOG -> {
                ClientState.fog = p.arg();
                ClientState.fogDistance = p.f();
            }
            case Fx.HEARTBEAT -> ClientState.heartbeat = p.arg();
            case Fx.WHITE_FLASH -> {
                ClientState.whiteFlash = calm ? 0 : p.arg();
                ClientState.whiteFlashMax = Math.max(1, p.arg());
            }
            case Fx.OPEN_NOTE -> mc.setScreen(new NoteScreen(p.text(), null));
            case Fx.MUSIC -> SoundSequencer.music(p.text());
            case Fx.SUBTITLE -> {
                ClientState.subtitle = p.text();
                ClientState.subtitleTicks = Math.max(20, p.arg());
            }
            case Fx.STOP_SEQUENCE -> SoundSequencer.stopAll();
            default -> {}
        }
    }
}
