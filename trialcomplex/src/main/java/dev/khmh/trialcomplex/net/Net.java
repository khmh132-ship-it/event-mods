package dev.khmh.trialcomplex.net;

import dev.khmh.trialcomplex.TrialComplex;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Сервер → клиент: голос, субтитры, HUD. Мод обязателен на обеих сторонах. */
public final class Net {
    private static final String VERSION = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TrialComplex.MODID, "main"), () -> VERSION, VERSION::equals,
            // для автотестов ботами (-Dtrialcomplex.allowVanilla=true) сервер пускает клиентов без мода
            Boolean.getBoolean("trialcomplex.allowVanilla") ? NetworkRegistry.acceptMissingOr(VERSION) : VERSION::equals);

    private Net() {}

    public static void register() {
        int id = 0;
        CH.messageBuilder(Voice.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Voice::encode).decoder(Voice::decode).consumerMainThread(Voice::handle).add();
        CH.messageBuilder(Hud.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Hud::encode).decoder(Hud::decode).consumerMainThread(Hud::handle).add();
    }

    public static void send(ServerPlayer p, Object msg) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    /** Проиграть реплику (lineId) или остановить текущую (lineId пустой). */
    public record Voice(String lineId) {
        static void encode(Voice m, FriendlyByteBuf b) { b.writeUtf(m.lineId); }
        static Voice decode(FriendlyByteBuf b) { return new Voice(b.readUtf()); }
        static void handle(Voice m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.khmh.trialcomplex.client.ClientState.onVoice(m.lineId));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Состояние HUD: номер испытания, название, цель. index < 0 — скрыть. */
    public record Hud(int index, int total, String title, String objective) {
        static void encode(Hud m, FriendlyByteBuf b) {
            b.writeVarInt(m.index); b.writeVarInt(m.total); b.writeUtf(m.title); b.writeUtf(m.objective);
        }
        static Hud decode(FriendlyByteBuf b) {
            return new Hud(b.readVarInt(), b.readVarInt(), b.readUtf(), b.readUtf());
        }
        static void handle(Hud m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.khmh.trialcomplex.client.ClientState.onHud(m));
            ctx.get().setPacketHandled(true);
        }
    }
}
