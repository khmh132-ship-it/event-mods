package com.echohorror.story;

import com.echohorror.EchoHorror;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Grants the code-driven advancements of the ECHO tab (all use an impossible criterion "done"). */
public final class Achievements {
    private Achievements() {}

    public static void award(ServerPlayer p, String id) {
        if (p == null || p.server == null) return;
        Advancement adv = p.server.getAdvancements().getAdvancement(new ResourceLocation(EchoHorror.MODID, "echo/" + id));
        if (adv != null) p.getAdvancements().award(adv, "done");
    }

    public static void awardAll(MinecraftServer server, String id) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) award(p, id);
    }
}
