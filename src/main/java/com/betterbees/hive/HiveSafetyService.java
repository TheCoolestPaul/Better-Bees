package com.betterbees.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

public final class HiveSafetyService {
    private HiveSafetyService() {}

    public static BeehiveBlockEntity loadedHive(ServerLevel level, BlockPos pos) {
        return pos != null && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive ? hive : null;
    }

    public static boolean isFireNearby(ServerLevel level, BeehiveBlockEntity hive) {
        return ((HiveRuntimeAccess) hive).betterbees$getRuntimeState().fireNearby(level.getGameTime(),
                () -> scanLoadedNeighborhood(level, hive.getBlockPos()));
    }

    /** An incomplete neighborhood is unsafe; validation must never pull in a chunk. */
    public static boolean scanLoadedNeighborhood(ServerLevel level, BlockPos pos) {
        for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (!level.hasChunkAt(nearby)
                    || level.getBlockState(nearby).getBlock() instanceof net.minecraft.world.level.block.FireBlock) return true;
        }
        return false;
    }
}
