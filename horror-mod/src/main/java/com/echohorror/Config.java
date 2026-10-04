package com.echohorror;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.BooleanValue AUTO_START;
    public static final ForgeConfigSpec.DoubleValue INTENSITY;
    public static final ForgeConfigSpec.DoubleValue SANITY_DRAIN;
    public static final ForgeConfigSpec.IntValue LOCATION_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue FAKE_SCREENS;
    public static final ForgeConfigSpec.BooleanValue FAKE_CHAT;
    public static final ForgeConfigSpec.BooleanValue WORLD_TAMPERING;
    public static final ForgeConfigSpec.BooleanValue JUMPSCARES;
    public static final ForgeConfigSpec.BooleanValue VOICE_MIMIC;
    public static final ForgeConfigSpec.BooleanValue HOSTILE_SPAWNS;

    public static final ForgeConfigSpec.BooleanValue REDUCE_FLASHING;
    public static final ForgeConfigSpec.BooleanValue SCREEN_EFFECTS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("story");
        AUTO_START = b.comment("Сюжет начинается сам примерно через 30 секунд после входа первого игрока.",
                        "false — мод спит, пока админ не введёт /echo start.")
                .define("autoStartOnJoin", true);
        LOCATION_DISTANCE = b.comment("Расстояние (в блоках) между сюжетными локациями")
                .defineInRange("locationDistance", 260, 64, 2000);
        b.pop();
        b.push("horror");
        INTENSITY = b.comment("Частота страшных событий (1.0 = норма, 2.0 = вдвое чаще)")
                .defineInRange("intensity", 1.0, 0.1, 5.0);
        SANITY_DRAIN = b.comment("Множитель потери рассудка")
                .defineInRange("sanityDrain", 1.0, 0.0, 5.0);
        FAKE_SCREENS = b.comment("Фальшивые экраны (смерть, отключение от сервера)").define("fakeScreens", true);
        FAKE_CHAT = b.comment("Фальшивые сообщения в чате от имени других игроков").define("fakeChat", true);
        WORLD_TAMPERING = b.comment("Мир меняется сам: гаснут факелы, открываются двери, появляются записки")
                .define("worldTampering", true);
        JUMPSCARES = b.comment("Скримеры").define("jumpscares", true);
        VOICE_MIMIC = b.comment("Эхо записывает голоса игроков (Simple Voice Chat) и повторяет их. Записи хранятся только в памяти.")
                .define("voiceMimic", true);
        HOSTILE_SPAWNS = b.comment("Появление враждебных существ Эха").define("hostileSpawns", true);
        b.pop();
        SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        REDUCE_FLASHING = c.comment("Уменьшить вспышки и мерцание экрана (фоточувствительность)").define("reduceFlashing", false);
        SCREEN_EFFECTS = c.comment("Эффекты экрана (виньетка, зерно, искажения)").define("screenEffects", true);
        CLIENT_SPEC = c.build();
    }

    private Config() {}
}
