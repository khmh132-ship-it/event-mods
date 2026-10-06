package dev.khmh.trialcomplex.voice;

import dev.khmh.trialcomplex.game.Complex;
import dev.khmh.trialcomplex.net.Net;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.*;

/**
 * Серверный диктор: очередь реплик, чтобы Голос не перебивал сам себя.
 * Все реплики слышат все игроки.
 */
public final class Voice {
    private record Req(String id, Runnable after) {}

    private final Complex cx;
    private final Deque<Req> queue = new ArrayDeque<>();
    private final RandomSource rnd = RandomSource.create();
    private final Map<String, String> lastInGroup = new HashMap<>();
    private Req current;
    private long busyUntil;
    private String lastSaid = "";

    public Voice(Complex cx) {
        this.cx = cx;
    }

    /** Поставить в очередь. */
    public void say(String id) {
        say(id, null);
    }

    public void say(String id, Runnable after) {
        if (id == null) {
            if (after != null) queue.add(new Req(null, after));
            return;
        }
        queue.add(new Req(id, after));
    }

    /** Перебить всё и сказать сразу. Отложенные действия текущей очереди выполняются, чтобы логика не зависла. */
    public void interrupt(String id, Runnable after) {
        List<Runnable> pending = new ArrayList<>();
        if (current != null && current.after != null) pending.add(current.after);
        for (Req r : queue) if (r.after != null) pending.add(r.after);
        queue.clear();
        current = null;
        busyUntil = 0;
        pending.forEach(Runnable::run);
        say(id, after);
    }

    public void interrupt(String id) {
        interrupt(id, null);
    }

    public void stopAll() {
        interrupt(null, null);
        for (ServerPlayer p : cx.players()) Net.send(p, new Net.Voice(""));
    }

    /** Случайная реплика из группы (без повтора предыдущей). Если группы нет — пробует id как есть. */
    public String pick(String group) {
        List<String> g = VoiceLines.group(group);
        if (g.isEmpty()) return VoiceLines.exists(group) ? group : null;
        if (g.size() == 1) return g.get(0);
        String last = lastInGroup.get(group);
        String id;
        do {
            id = g.get(rnd.nextInt(g.size()));
        } while (id.equals(last));
        lastInGroup.put(group, id);
        return id;
    }

    /** Реплика из группы с учётом игрока: сначала "group.khmh"/"group.itachi", иначе общая. */
    public String pickFor(String group, ServerPlayer p) {
        if (p != null) {
            String personal = group + "." + cx.roles().key(p);
            if (!VoiceLines.group(personal).isEmpty() || VoiceLines.exists(personal)) {
                // половину раз — персонально, чтобы не надоедало
                if (VoiceLines.group(group).isEmpty() || rnd.nextBoolean()) return pick(personal);
            }
        }
        return pick(group);
    }

    public void sayGroup(String group) {
        String id = pick(group);
        if (id != null) say(id);
    }

    public void sayGroupFor(String group, ServerPlayer p) {
        String id = pickFor(group, p);
        if (id != null) say(id);
    }

    public boolean busy() {
        return current != null || !queue.isEmpty();
    }

    public String lastSaid() {
        return lastSaid;
    }

    public void tick(long now) {
        if (current != null && now >= busyUntil) {
            Req done = current;
            current = null;
            if (done.after != null) done.after.run();
        }
        while (current == null && !queue.isEmpty()) {
            Req next = queue.poll();
            if (next.id == null) {
                if (next.after != null) next.after.run();
                continue;
            }
            VoiceLines.Line line = VoiceLines.get(next.id);
            int ms = line != null ? line.ms() : 1500;
            for (ServerPlayer p : cx.players()) Net.send(p, new Net.Voice(next.id));
            lastSaid = next.id;
            current = next;
            busyUntil = now + (ms + 350) / 50;
        }
    }
}
