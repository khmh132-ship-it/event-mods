package com.echohorror.network;

/** Client effect identifiers sent with {@link FxPacket}. */
public final class Fx {
    public static final int JUMPSCARE = 1;       // arg = face index
    public static final int FAKE_DISCONNECT = 2;
    public static final int FAKE_DEATH = 3;      // text = cause
    public static final int SCREEN_TEXT = 4;     // text, arg = ticks
    public static final int SHAKE = 5;           // arg = ticks, f = strength
    public static final int BLACKOUT = 6;        // arg = ticks
    public static final int FLICKER = 7;         // arg = ticks
    public static final int GLITCH = 8;          // arg = ticks
    public static final int CREDITS = 9;
    public static final int CHAPTER = 10;        // text = "title|subtitle"
    public static final int FOG = 11;            // arg = ticks, f = distance
    public static final int HEARTBEAT = 12;      // arg = ticks
    public static final int WHITE_FLASH = 13;    // arg = ticks
    public static final int OPEN_NOTE = 14;      // text = note id
    public static final int MUSIC = 15;          // text = sound name ("" = stop)
    public static final int SUBTITLE = 16;       // text, arg = ticks
    public static final int STOP_SEQUENCE = 17;

    private Fx() {}
}
