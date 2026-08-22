package com.betterbees.ai.tasks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Bee;

import java.util.Map;

public final class FloatTask extends Behavior<Bee> {
    public FloatTask() {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Bee bee) {
        return (bee.isInWater() && bee.getFluidHeight(FluidTags.WATER) > bee.getFluidJumpThreshold())
                || bee.isInLava()
                || bee.isLeashed();
    }

    @Override
    protected void start(ServerLevel level, Bee bee, long gameTime) {
        bee.getJumpControl().jump();
    }
}
