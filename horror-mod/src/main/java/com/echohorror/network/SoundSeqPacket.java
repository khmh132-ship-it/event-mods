package com.echohorror.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A timed sequence of non-positional sounds with subtitles (radio broadcasts, tapes).
 * Each step waits {@code delay} ticks after the previous step started.
 */
public record SoundSeqPacket(List<Step> steps) {
    public record Step(String sound, int delay, String subtitle, float volume) {}

    public static void encode(SoundSeqPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.steps.size());
        for (Step s : p.steps) {
            buf.writeUtf(s.sound);
            buf.writeVarInt(s.delay);
            buf.writeUtf(s.subtitle, 2048);
            buf.writeFloat(s.volume);
        }
    }

    public static SoundSeqPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Step> steps = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            steps.add(new Step(buf.readUtf(), buf.readVarInt(), buf.readUtf(2048), buf.readFloat()));
        }
        return new SoundSeqPacket(steps);
    }

    public static void handle(SoundSeqPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.echohorror.client.ClientHandlers.handleSequence(p)));
        ctx.get().setPacketHandled(true);
    }

    /** Fluent builder. */
    public static final class Builder {
        private final List<Step> steps = new ArrayList<>();

        public Builder sound(String sound, int delay) {
            steps.add(new Step(sound, delay, "", 1f));
            return this;
        }

        public Builder sound(String sound, int delay, String subtitle) {
            steps.add(new Step(sound, delay, subtitle, 1f));
            return this;
        }

        public Builder sub(String subtitle, int delay) {
            steps.add(new Step("", delay, subtitle, 1f));
            return this;
        }

        public SoundSeqPacket build() {
            return new SoundSeqPacket(new ArrayList<>(steps));
        }
    }
}
