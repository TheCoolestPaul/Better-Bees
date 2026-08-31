package com.betterbees.config;

/** Loader-neutral, immutable settings consumed by all Better Bees gameplay code. */
public record ConfigSnapshot(
        int maxWanderRadius,
        int flowerLocateRange,
        int searchAttempts,
        int flowerScanBudget,
        int flowerCacheSize,
        int hivePathFailuresBeforeBlacklist,
        int hiveCapacity,
        int honeyCapacity,
        int configuredHarvestCost,
        int configuredShearsHoneycombMin,
        int configuredShearsHoneycombMax,
        boolean indoorBreedingEnabled,
        int breedingIntervalTicks,
        double breedingChance,
        double configuredMinimumBeeScale,
        double configuredMaximumBeeScale,
        int hiveTransitionIntervalTicks,
        boolean adaptiveEntitySensing
) {
    public static ConfigSnapshot defaults() {
        return new ConfigSnapshot(25, 8, 10, 32, 512, 3, 20, 20, 1, 1, 3,
                true, 1200, 0.05D, 0.20D, 0.35D, 5, true);
    }

    public int harvestCost() { return Math.min(configuredHarvestCost, honeyCapacity); }
    public int shearsHoneycombMin() { return Math.min(configuredShearsHoneycombMin, configuredShearsHoneycombMax); }
    public int shearsHoneycombMax() { return Math.max(configuredShearsHoneycombMin, configuredShearsHoneycombMax); }
    public double minimumBeeScale() { return Math.min(configuredMinimumBeeScale, configuredMaximumBeeScale); }
    public double maximumBeeScale() { return Math.max(configuredMinimumBeeScale, configuredMaximumBeeScale); }
}
