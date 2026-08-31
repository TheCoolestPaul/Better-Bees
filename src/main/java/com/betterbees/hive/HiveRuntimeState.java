package com.betterbees.hive;

import java.util.function.BooleanSupplier;

/** Transient state owned by a loaded hive, never serialized or shared across worlds. */
public final class HiveRuntimeState {
    private long fireTick = Long.MIN_VALUE;
    private boolean fireNearby;
    private long lastTransitionTick = Long.MIN_VALUE;

    public boolean fireNearby(long tick, BooleanSupplier scan) {
        if (fireTick != tick) {
            fireNearby = scan.getAsBoolean();
            fireTick = tick;
        }
        return fireNearby;
    }

    public boolean allowTransitionSound(long tick, int interval) {
        if (interval <= 0) return true;
        if (lastTransitionTick != Long.MIN_VALUE && tick >= lastTransitionTick
                && tick - lastTransitionTick < interval) return false;
        lastTransitionTick = tick;
        return true;
    }
}
