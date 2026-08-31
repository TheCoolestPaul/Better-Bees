package com.betterbees.ai.sensors;

import com.betterbees.config.BetterBeesConfig;
import com.betterbees.mixin.BrainSensorsAccessor;
import com.betterbees.registry.ModSensorTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Bee;

public final class BeeSensing {
    private BeeSensing() {}

    public static void beforeBehaviors(ServerLevel level, Bee bee) {
        if (!BetterBeesConfig.adaptiveEntitySensing()) return;
        var sensors = ((BrainSensorsAccessor) bee.getBrain()).betterbees$getSensors();
        if (sensors.get(ModSensorTypes.BEE_NEARBY_ENTITIES.get()) instanceof BeeNearbySensor nearby
                && nearby.updateDemand(level, bee)
                && sensors.get(ModSensorTypes.BEE_NEAREST_ADULT.get()) instanceof BeeAdultSensor adult) {
            adult.refresh(level, bee);
        }
    }
}
