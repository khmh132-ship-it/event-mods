package dev.khmh.trialcomplex.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** Прогресс прохождения, хранится в мире (data/trialcomplex_progress.dat). */
public final class ProgressData extends SavedData {
    public static final String NAME = "trialcomplex_progress";

    public int stage;
    public boolean started;
    public long startedAt;
    public CompoundTag hints = new CompoundTag();     // roomId → сколько подсказок взято
    public CompoundTag skips = new CompoundTag();     // roomId → 1, если пропущено
    public CompoundTag fails = new CompoundTag();     // roomId → число ошибок
    public CompoundTag falls = new CompoundTag();     // роль → падения
    public CompoundTag roomTime = new CompoundTag();  // roomId → тиков на решение

    public static ProgressData load(CompoundTag t) {
        ProgressData d = new ProgressData();
        d.stage = t.getInt("stage");
        d.started = t.getBoolean("started");
        d.startedAt = t.getLong("startedAt");
        d.hints = t.getCompound("hints");
        d.skips = t.getCompound("skips");
        d.fails = t.getCompound("fails");
        d.falls = t.getCompound("falls");
        d.roomTime = t.getCompound("roomTime");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.putInt("stage", stage);
        t.putBoolean("started", started);
        t.putLong("startedAt", startedAt);
        t.put("hints", hints);
        t.put("skips", skips);
        t.put("fails", fails);
        t.put("falls", falls);
        t.put("roomTime", roomTime);
        return t;
    }

    public void inc(CompoundTag tag, String key) {
        tag.putInt(key, tag.getInt(key) + 1);
        setDirty();
    }

    public int sum(CompoundTag tag) {
        int s = 0;
        for (String k : tag.getAllKeys()) s += tag.getInt(k);
        return s;
    }
}
