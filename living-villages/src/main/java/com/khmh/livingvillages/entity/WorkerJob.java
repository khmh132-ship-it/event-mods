package com.khmh.livingvillages.entity;

import javax.annotation.Nullable;

/** What a hired worker does, and which building employs them. */
public enum WorkerJob {
    BUILDER("builder_workshop", "mason"),
    LUMBERJACK("lumberjack_hut", "leatherworker");

    private final String workplace;
    private final String outfit;

    WorkerJob(String workplace, String outfit) {
        this.workplace = workplace;
        this.outfit = outfit;
    }

    /** Building type id that employs this job. */
    public String workplace() {
        return workplace;
    }

    /** Vanilla villager profession whose clothes the worker wears. */
    public String outfit() {
        return outfit;
    }

    @Nullable
    public static WorkerJob forWorkplace(String typeId) {
        for (WorkerJob job : values()) {
            if (job.workplace.equals(typeId)) {
                return job;
            }
        }
        return null;
    }

    public static WorkerJob byId(int id) {
        WorkerJob[] all = values();
        return all[Math.floorMod(id, all.length)];
    }
}
