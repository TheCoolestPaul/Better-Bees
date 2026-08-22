package com.betterbees.compat;

import com.betterbees.config.BetterBeesConfig;
import com.betterbees.hive.HiveHoneyService;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

/** Authoritative values exposed to optional inspection-overlay integrations. */
public record HiveOverlayData(int honey, int honeyCapacity, int bees, int beeCapacity) {
    public static HiveOverlayData from(BeehiveBlockEntity hive) {
        return new HiveOverlayData(
                HiveHoneyService.get(hive),
                BetterBeesConfig.honeyCapacity(),
                hive.getOccupantCount(),
                BetterBeesConfig.hiveCapacity());
    }
}
