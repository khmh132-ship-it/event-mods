package com.echohorror.horror;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/** Per-player sanity (0..100), persisted in player persistent NBT. */
public final class Sanity {
    private static final String KEY = "echohorror";

    private Sanity() {}

    public static CompoundTag data(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(KEY)) root.put(KEY, new CompoundTag());
        return root.getCompound(KEY);
    }

    public static float get(ServerPlayer p) {
        CompoundTag d = data(p);
        return d.contains("sanity") ? d.getFloat("sanity") : 100f;
    }

    public static void set(ServerPlayer p, float v) {
        data(p).putFloat("sanity", Mth.clamp(v, 0f, 100f));
    }

    public static void add(ServerPlayer p, float delta) {
        set(p, get(p) + delta);
    }

    /** 0 = calm, 1 = completely broken. */
    public static float fear(ServerPlayer p) {
        return 1f - get(p) / 100f;
    }

    public static String describe(float s) {
        if (s > 80) return "Я в порядке. Пока.";
        if (s > 60) return "Не могу отделаться от ощущения, что за мной наблюдают.";
        if (s > 40) return "Руки дрожат. Я слышу то, чего нет. Или есть?";
        if (s > 20) return "Они повторяют мои мысли. Не спать. Не оборачиваться.";
        if (s > 5) return "Я не уверен, что это мой почерк.";
        return "ты уже один из нас";
    }
}
