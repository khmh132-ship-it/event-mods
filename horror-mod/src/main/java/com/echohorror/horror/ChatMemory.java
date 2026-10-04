package com.echohorror.horror;

import net.minecraft.util.RandomSource;

import java.util.*;

/** The Echo remembers what players say in chat — and repeats it later. Memory only, never saved. */
public final class ChatMemory {
    public record Line(UUID who, String name, String text, long time) {}

    private static final Map<UUID, Deque<Line>> LINES = new HashMap<>();

    private ChatMemory() {}

    public static synchronized void record(UUID who, String name, String text) {
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty() || text.startsWith("/") || text.length() > 120) return;
        Deque<Line> d = LINES.computeIfAbsent(who, k -> new ArrayDeque<>());
        d.addLast(new Line(who, name, text, System.currentTimeMillis()));
        while (d.size() > 40) d.removeFirst();
    }

    /** A remembered line said by someone other than {@code exclude} (or anyone if null), at least a minute old. */
    public static synchronized Optional<Line> random(RandomSource r, UUID exclude) {
        long cutoff = System.currentTimeMillis() - 60_000L;
        List<Line> all = new ArrayList<>();
        for (Map.Entry<UUID, Deque<Line>> e : LINES.entrySet()) {
            if (exclude != null && e.getKey().equals(exclude)) continue;
            for (Line l : e.getValue()) if (l.time < cutoff) all.add(l);
        }
        if (all.isEmpty()) return Optional.empty();
        return Optional.of(all.get(r.nextInt(all.size())));
    }

    public static synchronized Optional<Line> randomOf(RandomSource r, UUID who) {
        Deque<Line> d = LINES.get(who);
        if (d == null || d.isEmpty()) return Optional.empty();
        List<Line> l = new ArrayList<>(d);
        return Optional.of(l.get(r.nextInt(l.size())));
    }

    public static synchronized void clear() {
        LINES.clear();
    }
}
