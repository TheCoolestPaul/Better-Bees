package com.betterbees.mixin;

import com.betterbees.ai.sensors.SensorRangeAccess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Sensor.class)
public abstract class SensorRangeMixin implements SensorRangeAccess {
    @Shadow
    private void updateTargetingConditionRanges(LivingEntity entity) {
        throw new AssertionError();
    }

    @Override
    public void betterbees$prepareSensingRange(LivingEntity entity) {
        updateTargetingConditionRanges(entity);
    }
}
