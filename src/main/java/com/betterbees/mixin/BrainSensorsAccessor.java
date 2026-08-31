package com.betterbees.mixin;

import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(Brain.class)
public interface BrainSensorsAccessor {
    @Accessor("sensors")
    Map<SensorType<?>, Sensor<?>> betterbees$getSensors();
}
