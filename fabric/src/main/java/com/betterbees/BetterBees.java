package com.betterbees;

import com.betterbees.config.BetterBeesConfig;
import com.betterbees.registry.ModDataComponents;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.registry.ModSensorTypes;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;

public final class BetterBees implements ModInitializer {
    public static final String MOD_ID = "betterbees";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        ModMemoryTypes.initialize();
        ModSensorTypes.initialize();
        ModDataComponents.initialize();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> BetterBeesConfig.load(server));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            logEffectiveConfiguration();
            com.betterbees.validation.UpgradeFixture.runIfRequested(server);
        });
        LOGGER.info("Better Bees initialization complete");
    }

    private static void logEffectiveConfiguration() {
        LOGGER.info("Better Bees active: hive capacity={}, honey capacity={}, harvest cost={}, shears honeycomb={}-{}, indoor breeding={}, interval={} ticks, chance={}, flower scan budget={}, flower cache size={}, hive path failures={}, bee scale={}-{}.",
                BetterBeesConfig.hiveCapacity(), BetterBeesConfig.honeyCapacity(), BetterBeesConfig.harvestCost(),
                BetterBeesConfig.shearsHoneycombMin(), BetterBeesConfig.shearsHoneycombMax(),
                BetterBeesConfig.indoorBreedingEnabled(), BetterBeesConfig.breedingIntervalTicks(), BetterBeesConfig.breedingChance(),
                BetterBeesConfig.flowerScanBudget(), BetterBeesConfig.flowerCacheSize(),
                BetterBeesConfig.hivePathFailuresBeforeBlacklist(), BetterBeesConfig.minimumBeeScale(), BetterBeesConfig.maximumBeeScale());
        boolean normalized = BetterBeesConfig.configuredHarvestCost() > BetterBeesConfig.honeyCapacity()
                || BetterBeesConfig.configuredShearsHoneycombMin() > BetterBeesConfig.configuredShearsHoneycombMax()
                || BetterBeesConfig.configuredMinimumBeeScale() > BetterBeesConfig.configuredMaximumBeeScale();
        if (normalized) BetterBees.LOGGER.warn("Better Bees normalized one or more inverted/out-of-range configuration relationships; effective values are shown above.");
    }
}
