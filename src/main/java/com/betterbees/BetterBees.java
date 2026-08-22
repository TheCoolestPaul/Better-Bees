package com.betterbees;

import com.betterbees.config.BetterBeesConfig;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.registry.ModSensorTypes;
import com.betterbees.registry.ModDataComponents;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(BetterBees.MOD_ID)
public final class BetterBees {
    public static final String MOD_ID = "betterbees";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BetterBees(IEventBus modBus, ModContainer container) {
        ModMemoryTypes.REGISTER.register(modBus);
        ModSensorTypes.REGISTER.register(modBus);
        ModDataComponents.REGISTER.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, BetterBeesConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(this::serverStarted);
    }

    private void serverStarted(ServerStartedEvent event) {
        LOGGER.info(
                "Better Bees active: hive capacity={}, honey capacity={}, harvest cost={}, shears honeycomb={}-{}, indoor breeding={}, interval={} ticks, chance={}, flower scan budget={}, flower cache size={}, hive path failures={}.",
                BetterBeesConfig.hiveCapacity(),
                BetterBeesConfig.honeyCapacity(),
                BetterBeesConfig.harvestCost(),
                BetterBeesConfig.shearsHoneycombMin(),
                BetterBeesConfig.shearsHoneycombMax(),
                BetterBeesConfig.indoorBreedingEnabled(),
                BetterBeesConfig.breedingIntervalTicks(),
                BetterBeesConfig.breedingChance(),
                BetterBeesConfig.flowerScanBudget(),
                BetterBeesConfig.flowerCacheSize(),
                BetterBeesConfig.hivePathFailuresBeforeBlacklist()
        );
        if (BetterBeesConfig.configuredHarvestCost() > BetterBeesConfig.honeyCapacity()) {
            LOGGER.warn("Better Bees harvest_cost={} exceeds honey_capacity={}; effective harvest cost is {}.",
                    BetterBeesConfig.configuredHarvestCost(), BetterBeesConfig.honeyCapacity(), BetterBeesConfig.harvestCost());
        }
        if (BetterBeesConfig.configuredShearsHoneycombMin() > BetterBeesConfig.configuredShearsHoneycombMax()) {
            LOGGER.warn("Better Bees shears honeycomb bounds were inverted; effective range is {}-{}.",
                    BetterBeesConfig.shearsHoneycombMin(), BetterBeesConfig.shearsHoneycombMax());
        }
    }
}
