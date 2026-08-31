package com.betterbees.mixin;

import com.betterbees.ai.sensors.SensorRangeAccess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Sensor.class)
public abstract class SensorRangeMixin implements SensorRangeAccess {
    @Override
    public void betterbees$prepareSensingRange(LivingEntity entity) {
        // 1.21.1 has fixed targeting ranges and no per-entity preparation.
    }
}
