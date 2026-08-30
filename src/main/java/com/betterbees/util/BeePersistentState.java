package com.betterbees.util;

import net.minecraft.core.BlockPos;

/** Version-neutral state used by the serialization mixins. */
public interface BeePersistentState extends HiveMemory {
    int betterbees$getHoneyCooldown();

    void betterbees$setHoneyCooldown(int ticks);

    void betterbees$restoreMemorizedHome(BlockPos pos);

    void betterbees$finishPersistentLoad();
}
