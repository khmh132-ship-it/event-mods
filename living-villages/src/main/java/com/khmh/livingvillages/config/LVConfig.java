package com.khmh.livingvillages.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server config, stored per world in serverconfig/livingvillages-server.toml. */
public final class LVConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue NATURAL_FOUNDING;
    public static final ForgeConfigSpec.IntValue FOUNDING_SPACING;
    public static final ForgeConfigSpec.IntValue PRODUCTION_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue PRODUCTION_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MAX_CATCHUP_CYCLES;
    public static final ForgeConfigSpec.IntValue STORAGE_CAP;
    public static final ForgeConfigSpec.IntValue BUILD_INTERVAL;
    public static final ForgeConfigSpec.IntValue MAX_BUILD_STEPS_PER_SECOND;
    public static final ForgeConfigSpec.IntValue PLAN_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue AUTO_GROWTH;
    public static final ForgeConfigSpec.IntValue MAX_SLOPE;
    public static final ForgeConfigSpec.IntValue BIRTH_INTERVAL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("economy");
        PRODUCTION_INTERVAL = b.comment("Ticks between production cycles (1200 = one minute)")
                .defineInRange("productionInterval", 1200, 20, 72000);
        PRODUCTION_MULTIPLIER = b.comment("Multiplier for everything villages produce")
                .defineInRange("productionMultiplier", 1.0, 0.0, 100.0);
        MAX_CATCHUP_CYCLES = b.comment("How many missed production cycles are made up for when time passed unobserved")
                .defineInRange("maxCatchupCycles", 120, 0, 100000);
        BIRTH_INTERVAL = b.comment("Ticks between births in a village with free beds and food (6000 = 5 minutes)")
                .defineInRange("birthInterval", 6000, 20, 1000000);
        STORAGE_CAP = b.comment("Max amount of one item a village keeps per warehouse level (base village counts as one)")
                .defineInRange("storageCap", 512, 16, 1000000);
        b.pop();
        b.push("founding");
        NATURAL_FOUNDING = b.comment("New villages appear as small camps near players (vanilla villages are not generated)")
                .define("naturalFounding", true);
        FOUNDING_SPACING = b.comment("Minimum distance between villages, in blocks")
                .defineInRange("foundingSpacing", 320, 64, 10000);
        b.pop();
        b.push("construction");
        BUILD_INTERVAL = b.comment("Ticks per construction step (one block, or one column while levelling the site)")
                .defineInRange("buildInterval", 10, 1, 1200);
        MAX_BUILD_STEPS_PER_SECOND = b.comment("Upper bound on steps per second when catching up on unobserved time")
                .defineInRange("maxBuildStepsPerSecond", 64, 1, 4096);
        PLAN_INTERVAL = b.comment("Ticks between attempts to start a new building")
                .defineInRange("planInterval", 600, 20, 72000);
        AUTO_GROWTH = b.comment("Villages choose and start new buildings on their own")
                .define("autoGrowth", true);
        MAX_SLOPE = b.comment("Max height difference of the ground under a new building")
                .defineInRange("maxSlope", 5, 0, 16);
        b.pop();
        SPEC = b.build();
    }

    private LVConfig() {
    }
}
