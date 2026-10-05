package com.echohorror.client;

import java.util.ArrayList;
import java.util.List;

/** Everything the client knows about the horror state. */
public final class ClientState {
    public static float sanity = 100f;
    public static int chapter;
    public static int flags;

    public static String objective = "";
    public static List<String> notes = new ArrayList<>();
    public static List<String> places = new ArrayList<>();

    // effect timers (client ticks)
    public static int blackout, flicker, glitch, whiteFlash, whiteFlashMax, heartbeat, fog, shake;
    public static float fogDistance = 16f, shakeStrength;
    public static int jumpscare = -1, jumpscareFace;
    public static String screenText = "";
    public static int screenTextTicks;
    public static String chapterTitle = "", chapterSub = "";
    public static int chapterTicks;
    public static String subtitle = "";
    public static int subtitleTicks;
    public static long time;
    /** 0..1: something unseen-but-visible is staring at you right now. */
    public static float watched;

    private ClientState() {}

    public static void reset() {
        sanity = 100f;
        chapter = 0;
        flags = 0;
        objective = "";
        notes = new ArrayList<>();
        places = new ArrayList<>();
        blackout = flicker = glitch = whiteFlash = heartbeat = fog = shake = 0;
        jumpscare = -1;
        screenTextTicks = chapterTicks = subtitleTicks = 0;
        subtitle = "";
    }

    public static void tick() {
        time++;
        if (blackout > 0) blackout--;
        if (flicker > 0) flicker--;
        if (glitch > 0) glitch--;
        if (whiteFlash > 0) whiteFlash--;
        if (heartbeat > 0) heartbeat--;
        if (fog > 0) fog--;
        if (shake > 0) shake--;
        if (screenTextTicks > 0) screenTextTicks--;
        if (chapterTicks > 0) chapterTicks--;
        if (subtitleTicks > 0 && --subtitleTicks == 0) subtitle = "";
        if (jumpscare >= 0 && ++jumpscare > 16) jumpscare = -1;
    }

    public static boolean echoNight() {
        return (flags & 1) != 0;
    }

    public static boolean deep() {
        return (flags & 2) != 0;
    }

    public static boolean bossFight() {
        return (flags & 4) != 0;
    }
}
