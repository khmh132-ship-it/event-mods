package dev.khmh.trialcomplex.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Роли для персональных реплик: хост мира — «khmh», второй — «itachi».
 * Ник «khmh» тоже считается хостом (на случай выделенного сервера).
 */
public final class Roles {
    public static final String HOST = "khmh";
    public static final String GUEST = "itachi";
    private final MinecraftServer server;

    Roles(MinecraftServer server) {
        this.server = server;
    }

    public boolean isHost(ServerPlayer p) {
        String n = p.getGameProfile().getName();
        if (n.equalsIgnoreCase("khmh")) return true;
        if (n.toLowerCase().contains("itachi") || n.contains("Итачи")) return false;
        return server.isSingleplayerOwner(p.getGameProfile());
    }

    public String key(ServerPlayer p) {
        return isHost(p) ? HOST : GUEST;
    }

    /** Имя для субтитров/чата. */
    public String display(ServerPlayer p) {
        return isHost(p) ? "Кхмх" : "Итачи";
    }
}
