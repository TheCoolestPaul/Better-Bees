package com.betterbees.registry;

import com.betterbees.BetterBees;
import com.betterbees.ai.BeeAi;
import com.betterbees.ai.sensors.BeeSensor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.sensing.TemptingSensor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSensorTypes {
    public static final DeferredRegister<SensorType<?>> REGISTER =
            DeferredRegister.create(Registries.SENSOR_TYPE, BetterBees.MOD_ID);

    public static final DeferredHolder<SensorType<?>, SensorType<TemptingSensor>> BEE_TEMPTATIONS =
            REGISTER.register("bee_temptations", () -> new SensorType<>(() -> new TemptingSensor(BeeAi.getTemptations())));
    public static final DeferredHolder<SensorType<?>, SensorType<BeeSensor>> BEE_MEMORIES =
            REGISTER.register("bee_memories", () -> new SensorType<>(BeeSensor::new));

    private ModSensorTypes() {}
}
