package com.echohorror.network;

import com.echohorror.EchoHorror;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class Net {
    private static final String PROTOCOL = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EchoHorror.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, FxPacket.class, FxPacket::encode, FxPacket::decode, FxPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SoundSeqPacket.class, SoundSeqPacket::encode, SoundSeqPacket::decode, SoundSeqPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, StatePacket.class, StatePacket::encode, StatePacket::decode, StatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, JournalPacket.class, JournalPacket::encode, JournalPacket::decode, JournalPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void fx(ServerPlayer player, int type, int arg, float f, String text) {
        send(player, new FxPacket(type, arg, f, text));
    }

    public static void fx(ServerPlayer player, int type, int arg) {
        send(player, new FxPacket(type, arg, 0f, ""));
    }

    public static void fx(ServerPlayer player, int type, String text) {
        send(player, new FxPacket(type, 0, 0f, text));
    }
}
