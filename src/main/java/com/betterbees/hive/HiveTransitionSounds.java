package com.betterbees.hive;

import com.betterbees.config.BetterBeesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import java.util.function.Supplier;

/** Keeps a removed hive's own sound budget available only during its synchronous evacuation. */
public final class HiveTransitionSounds {
    private static final ThreadLocal<BeehiveBlockEntity> releasing = new ThreadLocal<>();

    private HiveTransitionSounds() {}

    public static <T> T duringRelease(BeehiveBlockEntity hive, Supplier<T> release) {
        BeehiveBlockEntity previous = releasing.get();
        releasing.set(hive);
        try {
            return release.get();
        } finally {
            if (previous == null) releasing.remove();
            else releasing.set(previous);
        }
    }

    public static boolean allowExit(Level level, BlockPos pos) {
        BeehiveBlockEntity hive = releasing.get();
        if (hive == null || hive.getLevel() != level || !hive.getBlockPos().equals(pos)) {
            hive = level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof BeehiveBlockEntity loaded ? loaded : null;
        }
        // Unknown third-party release callers still get sound; vanilla releases always have an owner.
        return hive == null || ((HiveRuntimeAccess) hive).betterbees$getRuntimeState()
                .allowTransitionSound(level.getGameTime(), BetterBeesConfig.hiveTransitionIntervalTicks());
    }
}
