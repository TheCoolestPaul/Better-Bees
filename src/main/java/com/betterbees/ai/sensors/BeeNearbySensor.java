package com.betterbees.ai.sensors;

import com.betterbees.config.BetterBeesConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;

/** Owns only scheduling diagnostics and demand flags; vanilla owns the actual snapshot. */
public final class BeeNearbySensor extends NearestLivingEntitySensor<Bee> {
    private ResourceKey<Level> dimension;
    private boolean initialized;
    private boolean needed;
    private boolean wasBaby;
    private long lastRefreshTick = Long.MIN_VALUE;
    private long scanCount;

    public static boolean needsNearbyEntities(Bee bee) {
        return bee.isBaby() || bee.isInLove() || bee.isAngry() || bee.hurtTime > 0
                || bee.getPersistentAngerTarget() != null || bee.getTarget() != null
                || bee.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET)
                || bee.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected void doTick(ServerLevel level, Bee bee) {
        if (!BetterBeesConfig.adaptiveEntitySensing()) {
            super.doTick(level, bee);
            scanCount++;
        } else if (needsNearbyEntities(bee)) {
            refresh(level, bee, false);
        }
    }

    /** Called after sensors, and before a baby adult-sensor read, to avoid stale wake-up data. */
    public boolean updateDemand(ServerLevel level, Bee bee) {
        ensureDimension(level);
        boolean current = needsNearbyEntities(bee);
        boolean baby = bee.isBaby();
        boolean babyWake = baby && (!initialized || !wasBaby);
        if (current && (!initialized || !needed || babyWake)) refresh(level, bee, true);
        if (!current && (!initialized || needed)) {
            bee.getBrain().eraseMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
            bee.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
            bee.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
        } else if (!baby && wasBaby) {
            bee.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
        }
        initialized = true;
        needed = current;
        wasBaby = baby;
        return babyWake;
    }

    private void refresh(ServerLevel level, Bee bee, boolean forced) {
        ensureDimension(level);
        if (lastRefreshTick == level.getGameTime()) return;
        if (forced) ((SensorRangeAccess) (Object) this).betterbees$prepareSensingRange(bee);
        super.doTick(level, bee);
        lastRefreshTick = level.getGameTime();
        scanCount++;
    }

    private void ensureDimension(ServerLevel level) {
        if (!level.dimension().equals(dimension)) {
            dimension = level.dimension();
            initialized = false;
            lastRefreshTick = Long.MIN_VALUE;
        }
    }

    public long scanCount() { return scanCount; }
}
