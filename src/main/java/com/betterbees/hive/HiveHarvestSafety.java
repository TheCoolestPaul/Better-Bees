package com.betterbees.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;

/** Extension point for integrations whose automated players do not disturb bees. */
public final class HiveHarvestSafety {
    private HiveHarvestSafety() {}

    public static boolean isSafe(Level level, BlockPos pos, Player player) {
        return CampfireBlock.isSmokeyPos(level, pos);
    }
}
