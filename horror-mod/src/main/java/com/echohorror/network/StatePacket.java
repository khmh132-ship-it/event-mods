package com.echohorror.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Periodic sync: sanity, chapter and world flags (bit 0 = echo night, bit 1 = in dark region). */
public record StatePacket(float sanity, int chapter, int flags) {
    public static final int FLAG_ECHO_NIGHT = 1;
    public static final int FLAG_DEEP = 2;
    public static final int FLAG_BOSS = 4;

    public static void encode(StatePacket p, FriendlyByteBuf buf) {
        buf.writeFloat(p.sanity);
        buf.writeVarInt(p.chapter);
        buf.writeVarInt(p.flags);
    }

    public static StatePacket decode(FriendlyByteBuf buf) {
        return new StatePacket(buf.readFloat(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(StatePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.echohorror.client.ClientHandlers.handleState(p)));
        ctx.get().setPacketHandled(true);
    }
}
