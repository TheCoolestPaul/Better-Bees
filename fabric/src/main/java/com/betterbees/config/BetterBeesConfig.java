package com.betterbees.config;

import com.betterbees.BetterBees;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;

/** Fabric TOML backend. Values are loaded once per server/world, matching NeoForge world-restart semantics. */
public final class BetterBeesConfig {
    private static volatile ConfigSnapshot snapshot = ConfigSnapshot.defaults();

    private BetterBeesConfig() {}

    public static void load(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig/betterbees-server.toml");
        try {
            Files.createDirectories(path.getParent());
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                ConfigSnapshot defaults = ConfigSnapshot.defaults();
                snapshot = new ConfigSnapshot(
                        integer(config, "ai.max_wander_radius", defaults.maxWanderRadius(), 1, 128),
                        integer(config, "ai.flower_locate_range", defaults.flowerLocateRange(), 1, 64),
                        integer(config, "ai.search_attempts", defaults.searchAttempts(), 1, 100),
                        integer(config, "ai.flower_scan_budget", defaults.flowerScanBudget(), 1, 512),
                        integer(config, "ai.flower_cache_size", defaults.flowerCacheSize(), 16, 4096),
                        integer(config, "ai.hive_path_failures_before_blacklist", defaults.hivePathFailuresBeforeBlacklist(), 1, 10),
                        integer(config, "hive.capacity", defaults.hiveCapacity(), 1, 64),
                        integer(config, "hive.honey_capacity", defaults.honeyCapacity(), 1, 64),
                        integer(config, "hive.harvest_cost", defaults.configuredHarvestCost(), 1, 64),
                        integer(config, "hive.shears_honeycomb_min", defaults.configuredShearsHoneycombMin(), 1, 64),
                        integer(config, "hive.shears_honeycomb_max", defaults.configuredShearsHoneycombMax(), 1, 64),
                        bool(config, "hive.indoor_breeding_enabled", defaults.indoorBreedingEnabled()),
                        integer(config, "hive.breeding_interval_ticks", defaults.breedingIntervalTicks(), 20, 72000),
                        decimal(config, "hive.breeding_chance", defaults.breedingChance(), 0.0D, 1.0D),
                        decimal(config, "appearance.minimum_bee_scale", defaults.configuredMinimumBeeScale(), 0.0625D, 1.0D),
                        decimal(config, "appearance.maximum_bee_scale", defaults.configuredMaximumBeeScale(), 0.0625D, 1.0D),
                        integer(config, "audio.hive_transition_interval_ticks", defaults.hiveTransitionIntervalTicks(), 0, 100),
                        bool(config, "ai.adaptive_entity_sensing", defaults.adaptiveEntitySensing()));
                config.setComment("ai.adaptive_entity_sensing", "Skip unused nearby-mob scans for quiet adult bees. Disable for mods consuming nearby-entity memories. Restart the world after changing.");
                config.save();
            }
        } catch (Exception exception) {
            snapshot = ConfigSnapshot.defaults();
            BetterBees.LOGGER.error("Could not load {}; using Better Bees defaults for this server run.", path, exception);
        }
    }

    private static int integer(CommentedFileConfig config, String key, int fallback, int min, int max) {
        Object raw = config.get(key);
        int value = raw instanceof Number number ? number.intValue() : fallback;
        if (raw == null) config.set(key, fallback);
        return Math.max(min, Math.min(max, value));
    }

    private static double decimal(CommentedFileConfig config, String key, double fallback, double min, double max) {
        Object raw = config.get(key);
        double value = raw instanceof Number number ? number.doubleValue() : fallback;
        if (raw == null) config.set(key, fallback);
        return Math.max(min, Math.min(max, value));
    }

    private static boolean bool(CommentedFileConfig config, String key, boolean fallback) {
        Object raw = config.get(key);
        if (raw == null) config.set(key, fallback);
        return raw instanceof Boolean value ? value : fallback;
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
