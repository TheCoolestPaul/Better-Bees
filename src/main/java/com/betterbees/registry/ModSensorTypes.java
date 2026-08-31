package com.betterbees.registry;

import com.betterbees.BetterBees;
import com.betterbees.ai.BeeAi;
import com.betterbees.ai.sensors.BeeSensor;
import com.betterbees.ai.sensors.BeeNearbySensor;
import com.betterbees.ai.sensors.BeeAdultSensor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.TemptingSensor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModSensorTypes {
    public static final DeferredRegister<SensorType<?>> REGISTER =
            DeferredRegister.create(Registries.SENSOR_TYPE, BetterBees.MOD_ID);

    public static final RegistryHandle<SensorType<TemptingSensor>> BEE_TEMPTATIONS =
            register("bee_temptations", () -> new SensorType<>(() -> new TemptingSensor(BeeAi.getTemptations())));
    public static final RegistryHandle<SensorType<BeeSensor>> BEE_MEMORIES =
            register("bee_memories", () -> new SensorType<>(BeeSensor::new));
    public static final RegistryHandle<SensorType<BeeNearbySensor>> BEE_NEARBY_ENTITIES =
            register("bee_nearby_entities", () -> new SensorType<>(BeeNearbySensor::new));
    public static final RegistryHandle<SensorType<BeeAdultSensor>> BEE_NEAREST_ADULT =
            register("bee_nearest_adult", () -> new SensorType<>(BeeAdultSensor::new));

    private ModSensorTypes() {}

    private static <T extends Sensor<?>> RegistryHandle<SensorType<T>> register(
            String name, Supplier<SensorType<T>> factory) {
        var holder = REGISTER.register(name, factory);
        return holder::get;
    }
}
