package com.betterbees.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

public final class HiveFlowerService {
    private HiveFlowerService() {}

    public static HiveFlowerIndex.Request request(ServerLevel level, BlockPos hivePos, Bee bee,
                                                   long handledGeneration) {
        if (level.getBlockEntity(hivePos) instanceof HiveFlowerKnowledge knowledge) {
            return knowledge.betterbees$getFlowerIndex().request(level, hivePos, bee, handledGeneration);
        }
        return new HiveFlowerIndex.Request(HiveFlowerIndex.Status.SCANNING, null, 0);
    }

    public static void release(ServerLevel level, BlockPos hivePos, Bee bee) {
        if (level.getBlockEntity(hivePos) instanceof HiveFlowerKnowledge knowledge) {
            knowledge.betterbees$getFlowerIndex().release(bee.getUUID());
        }
    }

    public static void invalidate(ServerLevel level, BlockPos hivePos, BlockPos flower) {
        if (level.getBlockEntity(hivePos) instanceof HiveFlowerKnowledge knowledge) {
            knowledge.betterbees$getFlowerIndex().invalidate(flower);
        }
    }

    public static void tick(ServerLevel level, BlockPos hivePos, BeehiveBlockEntity hive) {
        if (hive instanceof HiveFlowerKnowledge knowledge) {
            knowledge.betterbees$getFlowerIndex().tick(level, hivePos);
        }
    }
}
