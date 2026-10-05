package com.echohorror.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** {@code places}: "name|x|y|z" for every place the group has found. */
public record JournalPacket(boolean open, int chapter, String objective, List<String> notes, List<String> places) {
    public static void encode(JournalPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.open);
        buf.writeVarInt(p.chapter);
        buf.writeUtf(p.objective, 2048);
        buf.writeVarInt(p.notes.size());
        for (String s : p.notes) buf.writeUtf(s);
        buf.writeVarInt(p.places.size());
        for (String s : p.places) buf.writeUtf(s);
    }

    public static JournalPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        int ch = buf.readVarInt();
        String obj = buf.readUtf(2048);
        int n = buf.readVarInt();
        List<String> notes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) notes.add(buf.readUtf());
        int m = buf.readVarInt();
        List<String> places = new ArrayList<>(m);
        for (int i = 0; i < m; i++) places.add(buf.readUtf());
        return new JournalPacket(open, ch, obj, notes, places);
    }

    public static void handle(JournalPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.echohorror.client.ClientHandlers.handleJournal(p)));
        ctx.get().setPacketHandled(true);
    }
}
