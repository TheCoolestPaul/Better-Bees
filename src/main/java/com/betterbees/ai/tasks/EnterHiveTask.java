package com.betterbees.ai.tasks;

import com.betterbees.ai.BeeAi;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.util.HiveMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

import java.util.Map;

public final class EnterHiveTask extends Behavior<Bee> {
    public EnterHiveTask() {
        super(Map.of(ModMemoryTypes.WANTS_HIVE.get(), MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Bee bee) {
        BlockPos home = ((HiveMemory) bee).betterbees$getMemorizedHome();
        if (home == null || !bee.getBrain().getMemory(ModMemoryTypes.WANTS_HIVE.get()).orElse(false)
                || BeeAi.isHiveNearFire(level, bee) || !home.closerToCenterThan(bee.position(), 2.0)) {
            return false;
        }
        if (!(level.getBlockEntity(home) instanceof BeehiveBlockEntity hive)) {
            ((HiveMemory) bee).betterbees$dropHive(bee);
            return false;
        }
        if (hive.isFull()) {
            ((HiveMemory) bee).betterbees$dropAndBlacklistHive(bee);
            return false;
        }
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Bee bee, long gameTime) {
        return false;
    }

    @Override
    protected void start(ServerLevel level, Bee bee, long gameTime) {
        BlockPos home = ((HiveMemory) bee).betterbees$getMemorizedHome();
        if (home != null && level.getBlockEntity(home) instanceof BeehiveBlockEntity hive && !hive.isFull()) {
            hive.addOccupant(bee);
        }
    }
}
