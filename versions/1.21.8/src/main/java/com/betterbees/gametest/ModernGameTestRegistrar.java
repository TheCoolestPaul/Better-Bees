package com.betterbees.gametest;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.Locale;
import java.util.function.Consumer;

/** Adapts the shared test specification to the 1.21.8+ test registry without reflection. */
public final class ModernGameTestRegistrar {
    private ModernGameTestRegistrar() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ModernGameTestRegistrar::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(
                ResourceLocation.fromNamespaceAndPath("betterbees", "default"), new TestEnvironmentDefinition.AllOf());

        register(event, environment, "beehiveAcceptsConfiguredCapacity", BetterBeesGameTests::beehiveAcceptsConfiguredCapacity);
        register(event, environment, "beeNestUsesSameCapacity", BetterBeesGameTests::beeNestUsesSameCapacity);
        register(event, environment, "twoAdultsCreateOneStoredBaby", BetterBeesGameTests::twoAdultsCreateOneStoredBaby);
        register(event, environment, "oneAdultCannotBreed", BetterBeesGameTests::oneAdultCannotBreed);
        register(event, environment, "failedRollDoesNotBreed", BetterBeesGameTests::failedRollDoesNotBreed);
        register(event, environment, "fullHiveDoesNotBreed", BetterBeesGameTests::fullHiveDoesNotBreed);
        register(event, environment, "ageCooldownPreventsBreeding", BetterBeesGameTests::ageCooldownPreventsBreeding);
        register(event, environment, "twentyOccupantsSurviveNbtRoundTrip", BetterBeesGameTests::twentyOccupantsSurviveNbtRoundTrip);
        register(event, environment, "forwardUpgradeFixtureRetainsBetterBeesData", BetterBeesGameTests::forwardUpgradeFixtureRetainsBetterBeesData);
        register(event, environment, "newBeeHasBetterBeesBrain", BetterBeesGameTests::newBeeHasBetterBeesBrain);
        register(event, environment, "beeScaleIsStableUniformAndConfigurable", BetterBeesGameTests::beeScaleIsStableUniformAndConfigurable);
        register(event, environment, "nativeScaleAttributeControlsPhysicalBeeSize", BetterBeesGameTests::nativeScaleAttributeControlsPhysicalBeeSize);
        register(event, environment, "beeScaleUsesUuidWithoutPersistentModifierData", BetterBeesGameTests::beeScaleUsesUuidWithoutPersistentModifierData);
        register(event, environment, "bothHiveTypesStoreConfiguredHoney", BetterBeesGameTests::bothHiveTypesStoreConfiguredHoney);
        register(event, environment, "harvestCostConsumesExactlyOneHoney", BetterBeesGameTests::harvestCostConsumesExactlyOneHoney);
        register(event, environment, "honeySurvivesNbtRoundTrip", BetterBeesGameTests::honeySurvivesNbtRoundTrip);
        register(event, environment, "legacyHoneyMigratesOneForOne", BetterBeesGameTests::legacyHoneyMigratesOneForOne);
        register(event, environment, "defaultHoneyScalingMatchesPlan", BetterBeesGameTests::defaultHoneyScalingMatchesPlan);
        register(event, environment, "honeycombRollStaysWithinConfiguredRange", BetterBeesGameTests::honeycombRollStaysWithinConfiguredRange);
        register(event, environment, "bottleHarvestConsumesOneHoney", BetterBeesGameTests::bottleHarvestConsumesOneHoney);
        register(event, environment, "smokedShearsHarvestKeepsOccupants", BetterBeesGameTests::smokedShearsHarvestKeepsOccupants);
        register(event, environment, "unsafeHarvestReleasesBeesWithoutResettingHoney", BetterBeesGameTests::unsafeHarvestReleasesBeesWithoutResettingHoney);
        register(event, environment, "hiveItemComponentPreservesExactHoney", BetterBeesGameTests::hiveItemComponentPreservesExactHoney);
        register(event, environment, "dispenserShearsUseIncrementalHarvest", BetterBeesGameTests::dispenserShearsUseIncrementalHarvest);
        register(event, environment, "collectiveFlowerScanHonorsPerHiveBudget", BetterBeesGameTests::collectiveFlowerScanHonorsPerHiveBudget);
        register(event, environment, "discoveredFlowersAreSharedAndSoftReserved", BetterBeesGameTests::discoveredFlowersAreSharedAndSoftReserved);
        register(event, environment, "collectiveFlowerScanPausesAfterCompletedMiss", BetterBeesGameTests::collectiveFlowerScanPausesAfterCompletedMiss);
        register(event, environment, "nonFlowersUseOneConstantTimeCacheProbe", BetterBeesGameTests::nonFlowersUseOneConstantTimeCacheProbe);
        register(event, environment, "hundredHiveIndexesRespectAggregateBudget", BetterBeesGameTests::hundredHiveIndexesRespectAggregateBudget);
        register(event, environment, "overlayDataUsesAuthoritativeHiveValues", BetterBeesGameTests::overlayDataUsesAuthoritativeHiveValues);
    }

    private static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition> environment,
                                 String methodName, Consumer<GameTestHelper> body) {
        String path = methodName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        ResourceLocation name = ResourceLocation.fromNamespaceAndPath("betterbees", path);
        ResourceLocation structure = ResourceLocation.fromNamespaceAndPath("betterbees", "empty");
        TestData<Holder<TestEnvironmentDefinition>> data = new TestData<>(
                environment, structure, 100, 0, true, Rotation.NONE);
        event.registerTest(name, new TypedTest(data, body));
    }

    private static final class TypedTest extends GameTestInstance {
        private final Consumer<GameTestHelper> body;

        private TypedTest(TestData<Holder<TestEnvironmentDefinition>> data, Consumer<GameTestHelper> body) {
            super(data);
            this.body = body;
        }

        @Override public void run(GameTestHelper helper) { body.accept(helper); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return FunctionGameTestInstance.CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Better Bees shared test"); }
    }
}
