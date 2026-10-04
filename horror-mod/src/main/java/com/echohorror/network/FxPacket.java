package com.echohorror.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record FxPacket(int type, int arg, float f, String text) {
    public static void encode(FxPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.type);
        buf.writeVarInt(p.arg);
        buf.writeFloat(p.f);
        buf.writeUtf(p.text, 4096);
    }

    public static FxPacket decode(FriendlyByteBuf buf) {
        return new FxPacket(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readUtf(4096));
    }

    public static void handle(FxPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.echohorror.client.ClientHandlers.handleFx(p)));
        ctx.get().setPacketHandled(true);
    }
}
