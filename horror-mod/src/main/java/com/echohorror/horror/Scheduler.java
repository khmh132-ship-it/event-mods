package com.echohorror.horror;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tiny server-thread task scheduler driven by the server tick. */
public final class Scheduler {
    private record Task(long at, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static long now;

    private Scheduler() {}

    public static void schedule(int delayTicks, Runnable r) {
        synchronized (PENDING) {
            PENDING.add(new Task(now + Math.max(1, delayTicks), r));
        }
    }

    public static void tick() {
        now++;
        synchronized (PENDING) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Task> it = TASKS.iterator();
        List<Runnable> due = new ArrayList<>();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.at <= now) {
                due.add(t.run);
                it.remove();
            }
        }
        for (Runnable r : due) {
            try {
                r.run();
            } catch (Exception e) {
                com.echohorror.EchoHorror.LOG.error("Scheduled task failed", e);
            }
        }
    }

    public static void clear() {
        TASKS.clear();
        synchronized (PENDING) {
            PENDING.clear();
        }
    }
}
