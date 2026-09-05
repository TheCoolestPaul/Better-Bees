package com.betterbees.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class BetterBeesConfig {
    public static final ModConfigSpec SPEC;
    private static volatile ConfigSnapshot snapshot = ConfigSnapshot.defaults();

    private static final ModConfigSpec.IntValue MAX_WANDER_RADIUS;
    private static final ModConfigSpec.IntValue FLOWER_LOCATE_RANGE;
    private static final ModConfigSpec.IntValue SEARCH_ATTEMPTS;
    private static final ModConfigSpec.IntValue FLOWER_SCAN_BUDGET;
    private static final ModConfigSpec.IntValue FLOWER_CACHE_SIZE;
    private static final ModConfigSpec.IntValue HIVE_PATH_FAILURES_BEFORE_BLACKLIST;
    private static final ModConfigSpec.IntValue HIVE_CAPACITY;
    private static final ModConfigSpec.IntValue HONEY_CAPACITY;
    private static final ModConfigSpec.IntValue HARVEST_COST;
    private static final ModConfigSpec.IntValue SHEARS_HONEYCOMB_MIN;
    private static final ModConfigSpec.IntValue SHEARS_HONEYCOMB_MAX;
    private static final ModConfigSpec.BooleanValue INDOOR_BREEDING_ENABLED;
    private static final ModConfigSpec.IntValue BREEDING_INTERVAL_TICKS;
    private static final ModConfigSpec.DoubleValue BREEDING_CHANCE;
    private static final ModConfigSpec.DoubleValue MINIMUM_BEE_SCALE;
    private static final ModConfigSpec.DoubleValue MAXIMUM_BEE_SCALE;
    private static final ModConfigSpec.IntValue HIVE_TRANSITION_INTERVAL_TICKS;
    private static final ModConfigSpec.BooleanValue ADAPTIVE_ENTITY_SENSING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("ai");
        ADAPTIVE_ENTITY_SENSING = builder
                .comment("Skip unused nearby-mob scans for quiet adult bees. Disable for mods that independently consume bee nearby-entity memories.")
                .worldRestart().define("adaptive_entity_sensing", true);
        MAX_WANDER_RADIUS = builder
                .comment("Maximum radius in blocks that bees wander from their memorized hive.")
                .worldRestart()
                .defineInRange("max_wander_radius", 25, 1, 128);
        FLOWER_LOCATE_RANGE = builder
                .comment("Radius in blocks in which bees search for flowers.")
                .worldRestart()
                .defineInRange("flower_locate_range", 8, 1, 64);
        SEARCH_ATTEMPTS = builder
                .comment("Failed flower searches before a bee returns to its hive.")
                .worldRestart()
                .defineInRange("search_attempts", 10, 1, 100);
        FLOWER_SCAN_BUDGET = builder
                .comment("Maximum flower positions examined by each active hive per tick.")
                .worldRestart()
                .defineInRange("flower_scan_budget", 32, 1, 512);
        FLOWER_CACHE_SIZE = builder
                .comment("Maximum transient flower positions remembered by each loaded hive.")
                .worldRestart()
                .defineInRange("flower_cache_size", 512, 16, 4096);
        HIVE_PATH_FAILURES_BEFORE_BLACKLIST = builder
                .comment("Consecutive path-creation failures before a bee blacklists its hive.")
                .worldRestart()
                .defineInRange("hive_path_failures_before_blacklist", 3, 1, 10);
        builder.pop();

        builder.push("hive");
        HIVE_CAPACITY = builder
                .comment("Maximum occupants in vanilla beehives and bee nests.")
                .worldRestart()
                .defineInRange("capacity", 20, 1, 64);
        HONEY_CAPACITY = builder
                .comment("Maximum honey stored in vanilla beehives and bee nests.")
                .worldRestart()
                .defineInRange("honey_capacity", 20, 1, 64);
        HARVEST_COST = builder
                .comment("Honey consumed by one successful bottle or shears harvest. Values above honey_capacity are clamped at runtime.")
                .worldRestart()
                .defineInRange("harvest_cost", 1, 1, 64);
        SHEARS_HONEYCOMB_MIN = builder
                .comment("Minimum honeycomb dropped by one shears harvest.")
                .worldRestart()
                .defineInRange("shears_honeycomb_min", 1, 1, 64);
        SHEARS_HONEYCOMB_MAX = builder
                .comment("Maximum honeycomb dropped by one shears harvest. Inverted min/max values are sorted at runtime.")
                .worldRestart()
                .defineInRange("shears_honeycomb_max", 3, 1, 64);
        INDOOR_BREEDING_ENABLED = builder
                .comment("Allow two eligible adult occupants to create a baby inside their hive.")
                .worldRestart()
                .define("indoor_breeding_enabled", true);
        BREEDING_INTERVAL_TICKS = builder
                .comment("Ticks between breeding rolls for each loaded hive.")
                .worldRestart()
                .defineInRange("breeding_interval_ticks", 1200, 20, 72000);
        BREEDING_CHANCE = builder
                .comment("Chance from 0.0 to 1.0 that an eligible hive produces one baby per roll.")
                .worldRestart()
                .defineInRange("breeding_chance", 0.05D, 0.0D, 1.0D);
        builder.pop();

        builder.push("appearance");
        MINIMUM_BEE_SCALE = builder
                .comment("Smallest physical scale assigned to an individual bee. Set both scale values to 1.0 for vanilla size.")
                .worldRestart()
                .defineInRange("minimum_bee_scale", 0.20D, 0.0625D, 1.0D);
        MAXIMUM_BEE_SCALE = builder
                .comment("Largest physical scale assigned to an individual bee. Inverted bounds are sorted at runtime.")
                .worldRestart()
                .defineInRange("maximum_bee_scale", 0.50D, 0.0625D, 1.0D);
        builder.pop();

        builder.push("audio");
        HIVE_TRANSITION_INTERVAL_TICKS = builder
                .comment("Minimum ticks between combined entry/exit sounds per hive. 0 restores unrestricted sound playback; bee timing is unchanged.")
                .worldRestart().defineInRange("hive_transition_interval_ticks", 5, 0, 100);
        builder.pop();
        SPEC = builder.build();
    }

    private BetterBeesConfig() {}

    public static void refreshSnapshot() {
        snapshot = new ConfigSnapshot(
                MAX_WANDER_RADIUS.getAsInt(), FLOWER_LOCATE_RANGE.getAsInt(), SEARCH_ATTEMPTS.getAsInt(),
                FLOWER_SCAN_BUDGET.getAsInt(), FLOWER_CACHE_SIZE.getAsInt(), HIVE_PATH_FAILURES_BEFORE_BLACKLIST.getAsInt(),
                HIVE_CAPACITY.getAsInt(), HONEY_CAPACITY.getAsInt(), HARVEST_COST.getAsInt(),
                SHEARS_HONEYCOMB_MIN.getAsInt(), SHEARS_HONEYCOMB_MAX.getAsInt(), INDOOR_BREEDING_ENABLED.getAsBoolean(),
                BREEDING_INTERVAL_TICKS.getAsInt(), BREEDING_CHANCE.getAsDouble(),
                MINIMUM_BEE_SCALE.getAsDouble(), MAXIMUM_BEE_SCALE.getAsDouble(), HIVE_TRANSITION_INTERVAL_TICKS.getAsInt(),
                ADAPTIVE_ENTITY_SENSING.getAsBoolean());
    }

    public static ConfigSnapshot snapshot() { return snapshot; }
    public static boolean adaptiveEntitySensing() { return snapshot.adaptiveEntitySensing(); }
    public static int hiveTransitionIntervalTicks() { return snapshot.hiveTransitionIntervalTicks(); }
    public static int maxWanderRadius() { return snapshot.maxWanderRadius(); }
    public static int flowerLocateRange() { return snapshot.flowerLocateRange(); }
    public static int searchAttempts() { return snapshot.searchAttempts(); }
    public static int flowerScanBudget() { return snapshot.flowerScanBudget(); }
    public static int flowerCacheSize() { return snapshot.flowerCacheSize(); }
    public static int hivePathFailuresBeforeBlacklist() { return snapshot.hivePathFailuresBeforeBlacklist(); }
    public static int hiveCapacity() { return snapshot.hiveCapacity(); }
    public static int honeyCapacity() { return snapshot.honeyCapacity(); }
    public static int configuredHarvestCost() { return snapshot.configuredHarvestCost(); }
    public static int harvestCost() { return snapshot.harvestCost(); }
    public static int configuredShearsHoneycombMin() { return snapshot.configuredShearsHoneycombMin(); }
    public static int configuredShearsHoneycombMax() { return snapshot.configuredShearsHoneycombMax(); }
    public static int shearsHoneycombMin() { return snapshot.shearsHoneycombMin(); }
    public static int shearsHoneycombMax() { return snapshot.shearsHoneycombMax(); }
    public static boolean indoorBreedingEnabled() { return snapshot.indoorBreedingEnabled(); }
    public static int breedingIntervalTicks() { return snapshot.breedingIntervalTicks(); }
    public static double breedingChance() { return snapshot.breedingChance(); }
    public static double configuredMinimumBeeScale() { return snapshot.configuredMinimumBeeScale(); }
    public static double configuredMaximumBeeScale() { return snapshot.configuredMaximumBeeScale(); }
    public static double minimumBeeScale() { return snapshot.minimumBeeScale(); }
    public static double maximumBeeScale() { return snapshot.maximumBeeScale(); }
}
