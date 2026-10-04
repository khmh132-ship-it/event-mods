package com.echohorror.horror;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Bridge to the optional Simple Voice Chat integration. This class must not reference any voicechat classes;
 * the plugin (compat.VoiceMimicPlugin) installs itself here when voice chat is present.
 */
public final class VoiceBridge {
    public interface Impl {
        boolean hasClip(UUID speaker);

        /** Plays a remembered clip of {@code speaker} at {@code pos}, audible only to {@code listener}. */
        boolean play(ServerPlayer listener, UUID speaker, Vec3 pos);
    }

    private static volatile Impl impl;

    private VoiceBridge() {}

    public static void install(Impl i) {
        impl = i;
    }

    public static boolean available() {
        return impl != null && com.echohorror.Config.VOICE_MIMIC.get();
    }

    public static boolean hasClip(UUID speaker) {
        return available() && impl.hasClip(speaker);
    }

    public static boolean play(ServerPlayer listener, UUID speaker, Vec3 pos) {
        return available() && impl.play(listener, speaker, pos);
    }
}
