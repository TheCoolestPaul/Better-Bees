package com.betterbees.ai.sensors;

import net.minecraft.world.entity.LivingEntity;

/** Runs vanilla's range preparation without advancing the native sensor countdown. */
public interface SensorRangeAccess {
    void betterbees$prepareSensingRange(LivingEntity entity);
}
