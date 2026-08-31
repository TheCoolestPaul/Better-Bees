package com.betterbees.ai.tasks;

import com.betterbees.hive.HiveSafetyService;
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
                || !home.closerToCenterThan(bee.position(), 2.0) || BeeAi.isHiveNearFire(level, bee)) {
            return false;
        }
        BeehiveBlockEntity hive = HiveSafetyService.loadedHive(level, home);
        if (hive == null) {
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
        BeehiveBlockEntity hive = HiveSafetyService.loadedHive(level, home);
        if (hive != null && !hive.isFull()
                && !HiveSafetyService.scanLoadedNeighborhood(level, home)) {
            hive.addOccupant(bee);
        }
    }
}
