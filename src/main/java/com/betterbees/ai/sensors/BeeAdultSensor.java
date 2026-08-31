package com.betterbees.ai.sensors;

import com.betterbees.config.BetterBeesConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ai.sensing.AdultSensor;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;

public final class BeeAdultSensor extends AdultSensor {
    private ResourceKey<Level> dimension;
    private long lastRefreshTick = Long.MIN_VALUE;
    private long scanCount;

    @Override
    protected void doTick(ServerLevel level, AgeableMob entity) {
        if (!BetterBeesConfig.adaptiveEntitySensing() || !(entity instanceof Bee bee)) {
            super.doTick(level, entity);
            scanCount++;
        } else if (bee.isBaby()) {
            // This sensor may be scheduled before the nearby sensor during a baby's first tick.
            BeeSensing.beforeBehaviors(level, bee);
            refresh(level, bee, false);
        }
    }

    public void refresh(ServerLevel level, Bee bee) {
        refresh(level, bee, true);
    }

    private void refresh(ServerLevel level, Bee bee, boolean forced) {
        if (level.dimension().equals(dimension) && lastRefreshTick == level.getGameTime()) return;
        if (forced) ((SensorRangeAccess) (Object) this).betterbees$prepareSensingRange(bee);
        super.doTick(level, bee);
        dimension = level.dimension();
        lastRefreshTick = level.getGameTime();
        scanCount++;
    }

    public long scanCount() { return scanCount; }
}
