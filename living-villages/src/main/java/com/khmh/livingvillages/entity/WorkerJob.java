package com.khmh.livingvillages.entity;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/** What a hired worker does, and which building employs them. */
public enum WorkerJob {
    BUILDER("builder_workshop", "mason"),
    LUMBERJACK("lumberjack_hut", "leatherworker"),
    MINER("mine", "toolsmith"),
    APPRENTICE("apprentice_workshop", "nitwit"),
    /** A vanilla farmer taken over by the village: works every field around it. */
    FARMER("", "farmer"),
    SHEPHERD("", "shepherd"),
    BUTCHER("", "butcher"),
    GUARD("barracks", "weaponsmith"),
    MASON("", "mason"),
    TOOLSMITH("", "toolsmith"),
    WEAPONSMITH("", "weaponsmith"),
    ARMORER("", "armorer"),
    FLETCHER("", "fletcher"),
    LEATHERWORKER("", "leatherworker"),
    CARPENTER("sawmill", "fletcher");

    /** Jobs that make things to order (see {@link com.khmh.livingvillages.economy.Trades}). */
    public boolean crafts() {
        return this == APPRENTICE || this == MASON || this == TOOLSMITH || this == WEAPONSMITH || this == ARMORER
                || this == FLETCHER || this == LEATHERWORKER || this == CARPENTER;
    }

    private final String workplace;
    private final String outfit;

    WorkerJob(String workplace, String outfit) {
        this.workplace = workplace;
        this.outfit = outfit;
    }

    /** The kind of tool the job works with; null for jobs done by hand. */
    @Nullable
    public TagKey<Item> toolTag() {
        return switch (this) {
            case LUMBERJACK -> ItemTags.AXES;
            case MINER -> ItemTags.PICKAXES;
            case FARMER -> ItemTags.HOES;
            case SHEPHERD -> net.minecraftforge.common.Tags.Items.SHEARS;
            case BUTCHER -> ItemTags.AXES;
            case GUARD -> ItemTags.SWORDS;
            default -> null;
        };
    }

    /** The cheapest tool that does the job, asked for when there is none. */
    public Item basicTool() {
        return switch (this) {
            case LUMBERJACK -> Items.STONE_AXE;
            case MINER -> Items.STONE_PICKAXE;
            case FARMER -> Items.STONE_HOE;
            case SHEPHERD -> Items.SHEARS;
            case BUTCHER -> Items.STONE_AXE;
            case GUARD -> Items.STONE_SWORD;
            default -> Items.AIR;
        };
    }

    /** Hired for the whole village as soon as there is work for them, before their workplace exists. */
    public boolean villageWide() {
        return true; // every job can start before its workplace exists
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
