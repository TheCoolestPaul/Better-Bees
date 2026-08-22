package com.betterbees.ai.sensors;

import com.betterbees.registry.ModMemoryTypes;
import com.google.common.collect.ImmutableSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;

import java.util.Set;

public final class BeeSensor extends Sensor<LivingEntity> {
    @Override
    protected void doTick(ServerLevel level, LivingEntity entity) {
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(
                ModMemoryTypes.FLOWER_POS.get(),
                ModMemoryTypes.LAST_PATH.get(),
                ModMemoryTypes.HIVE_BLACKLIST.get(),
                ModMemoryTypes.POLLINATING_COOLDOWN.get(),
                ModMemoryTypes.POLLINATING_TICKS.get(),
                ModMemoryTypes.SUCCESSFUL_POLLINATING_TICKS.get(),
                ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get(),
                ModMemoryTypes.TRAVELLING_TICKS.get(),
                ModMemoryTypes.SEARCH_ATTEMPTS.get(),
                ModMemoryTypes.STUCK_TICKS.get(),
                ModMemoryTypes.WANTS_HIVE.get()
        );
    }
}
