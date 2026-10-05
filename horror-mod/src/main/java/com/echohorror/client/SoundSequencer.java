package com.echohorror.client;

import com.echohorror.EchoHorror;
import com.echohorror.network.SoundSeqPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Plays radio broadcasts / tapes: non-positional sounds with timed subtitles. */
public final class SoundSequencer {
    private static final class Running {
        final List<SoundSeqPacket.Step> steps;
        int index;
        int countdown;
        final List<SoundInstance> playing = new ArrayList<>();

        Running(List<SoundSeqPacket.Step> steps) {
            this.steps = steps;
            this.countdown = steps.isEmpty() ? 0 : steps.get(0).delay();
        }
    }

    private static final List<Running> RUNNING = new ArrayList<>();
    private static SoundInstance music;

    private SoundSequencer() {}

    public static SoundInstance flat(String sound, float volume, boolean loop) {
        return new SimpleSoundInstance(new ResourceLocation(EchoHorror.MODID, sound), SoundSource.MASTER, volume, 1f,
                RandomSource.create(), loop, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
    }

    public static void play(SoundSeqPacket p) {
        RUNNING.add(new Running(p.steps()));
    }

    public static void stopAll() {
        Minecraft mc = Minecraft.getInstance();
        for (Running r : RUNNING) for (SoundInstance s : r.playing) mc.getSoundManager().stop(s);
        RUNNING.clear();
        ClientState.subtitle = "";
    }

    public static void music(String sound) {
        Minecraft mc = Minecraft.getInstance();
        if (music != null) mc.getSoundManager().stop(music);
        music = null;
        musicName = sound == null ? "" : sound;
        musicOrphanTicks = 0;
        if (sound != null && !sound.isEmpty()) {
            music = flat(sound, 0.8f, true);
            mc.getMusicManager().stopPlaying();
            mc.getSoundManager().play(music);
        }
    }

    private static String musicName = "";
    private static int musicOrphanTicks;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        // boss music outlives the fight (players fled, died, reset): fade it after 10 seconds away
        if (music != null && "music.finale".equals(musicName) && !ClientState.bossFight()) {
            if (++musicOrphanTicks > 200) music(null);
        } else musicOrphanTicks = 0;
        if (music != null) {
            if (!mc.getSoundManager().isActive(music)) mc.getSoundManager().play(music);
            mc.getMusicManager().stopPlaying();
        }
        Iterator<Running> it = RUNNING.iterator();
        while (it.hasNext()) {
            Running r = it.next();
            r.countdown--;
            while (r.countdown <= 0 && r.index < r.steps.size()) {
                SoundSeqPacket.Step s = r.steps.get(r.index);
                if (!s.sound().isEmpty()) {
                    SoundInstance si = flat(s.sound(), s.volume(), false);
                    r.playing.add(si);
                    mc.getSoundManager().play(si);
                    if (!s.subtitle().isEmpty()) setSubtitle(s.subtitle());
                } else if (s.subtitle().isEmpty()) {
                    ClientState.subtitle = "";
                } else {
                    setSubtitle(s.subtitle());
                }
                r.index++;
                r.countdown = r.index < r.steps.size() ? r.steps.get(r.index).delay() : 0;
            }
            if (r.index >= r.steps.size()) it.remove();
        }
    }

    private static void setSubtitle(String s) {
        ClientState.subtitle = s;
        ClientState.subtitleTicks = 220;
    }

    public static void reset() {
        stopAll();
        music(null);
    }
}
