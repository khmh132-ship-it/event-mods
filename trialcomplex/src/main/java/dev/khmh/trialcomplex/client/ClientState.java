package dev.khmh.trialcomplex.client;

import dev.khmh.trialcomplex.TrialComplex;
import dev.khmh.trialcomplex.net.Net;
import dev.khmh.trialcomplex.voice.VoiceLines;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Клиентское состояние: текущая реплика Голоса (звук + субтитр) и HUD испытания. */
public final class ClientState {
    static SimpleSoundInstance playing;
    static String subtitle = "";
    static long subtitleStart, subtitleUntil;

    static Net.Hud hud = new Net.Hud(-1, 0, "", "");
    static long hudChanged;

    private ClientState() {}

    public static void onVoice(String lineId) {
        Minecraft mc = Minecraft.getInstance();
        if (playing != null) {
            mc.getSoundManager().stop(playing);
            playing = null;
        }
        if (lineId.isEmpty()) {
            subtitleUntil = 0;
            return;
        }
        VoiceLines.Line line = VoiceLines.get(lineId);
        ResourceLocation rl = new ResourceLocation(TrialComplex.MODID, "voice." + lineId);
        playing = new SimpleSoundInstance(rl, SoundSource.VOICE, 1.0f, 1.0f, RandomSource.create(),
                false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        mc.getSoundManager().play(playing);
        if (line != null) {
            subtitle = line.text();
            subtitleStart = Util.getMillis();
            subtitleUntil = subtitleStart + line.ms() + 1500;
        }
    }

    public static void onHud(Net.Hud h) {
        if (h.index() != hud.index() || !h.objective().equals(hud.objective())) hudChanged = Util.getMillis();
        hud = h;
    }
}
