package com.betterbees.hive;

public interface HiveHoneyStorage {
    int betterbees$getHoney();

    void betterbees$setHoney(int honey);

    boolean betterbees$isHoneyDisplayDirty();

    void betterbees$markHoneyDisplaySynced();

    void betterbees$setLoadingOccupants(boolean loading);

    void betterbees$restoreHoney(int honey);
}
