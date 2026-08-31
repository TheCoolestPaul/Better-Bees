package com.betterbees.registry;

import com.betterbees.BetterBees;
import com.betterbees.ai.BeeAi;
import com.betterbees.ai.sensors.BeeSensor;
import com.betterbees.ai.sensors.BeeNearbySensor;
import com.betterbees.ai.sensors.BeeAdultSensor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.sensing.TemptingSensor;

public final class ModSensorTypes {
    private static final FabricRegistryHandle<SensorType<TemptingSensor>> TEMPTATIONS_HANDLE = new FabricRegistryHandle<>();
    private static final FabricRegistryHandle<SensorType<BeeSensor>> MEMORIES_HANDLE = new FabricRegistryHandle<>();
    private static final FabricRegistryHandle<SensorType<BeeNearbySensor>> NEARBY_HANDLE = new FabricRegistryHandle<>();
    private static final FabricRegistryHandle<SensorType<BeeAdultSensor>> ADULT_HANDLE = new FabricRegistryHandle<>();
    public static final RegistryHandle<SensorType<TemptingSensor>> BEE_TEMPTATIONS = TEMPTATIONS_HANDLE;
    public static final RegistryHandle<SensorType<BeeSensor>> BEE_MEMORIES = MEMORIES_HANDLE;
    public static final RegistryHandle<SensorType<BeeNearbySensor>> BEE_NEARBY_ENTITIES = NEARBY_HANDLE;
    public static final RegistryHandle<SensorType<BeeAdultSensor>> BEE_NEAREST_ADULT = ADULT_HANDLE;
    private static boolean initialized;

    private ModSensorTypes() {}
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        TEMPTATIONS_HANDLE.bind(Registry.register(BuiltInRegistries.SENSOR_TYPE,
                ResourceLocation.fromNamespaceAndPath(BetterBees.MOD_ID, "bee_temptations"),
                new SensorType<>(() -> new TemptingSensor(BeeAi.getTemptations()))));
        MEMORIES_HANDLE.bind(Registry.register(BuiltInRegistries.SENSOR_TYPE,
                ResourceLocation.fromNamespaceAndPath(BetterBees.MOD_ID, "bee_memories"), new SensorType<>(BeeSensor::new)));
        NEARBY_HANDLE.bind(Registry.register(BuiltInRegistries.SENSOR_TYPE,
                ResourceLocation.fromNamespaceAndPath(BetterBees.MOD_ID, "bee_nearby_entities"), new SensorType<>(BeeNearbySensor::new)));
        ADULT_HANDLE.bind(Registry.register(BuiltInRegistries.SENSOR_TYPE,
                ResourceLocation.fromNamespaceAndPath(BetterBees.MOD_ID, "bee_nearest_adult"), new SensorType<>(BeeAdultSensor::new)));
    }
}
