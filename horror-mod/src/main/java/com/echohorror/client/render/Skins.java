package com.echohorror.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Resolves the skin of a (usually online) player for mimics. */
public final class Skins {
    public record Skin(ResourceLocation texture, boolean slim) {}

    private Skins() {}

    public static Skin of(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (id != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) return new Skin(info.getSkinLocation(), "slim".equals(info.getModelName()));
        }
        if (id == null) return new Skin(DefaultPlayerSkin.getDefaultSkin(), false);
        return new Skin(DefaultPlayerSkin.getDefaultSkin(id), "slim".equals(DefaultPlayerSkin.getSkinModelName(id)));
    }
}
