package com.betterbees.util;

import com.betterbees.registry.ModMemoryTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.animal.Bee;

import java.util.ArrayList;
import java.util.List;

public interface HiveMemory {
    BlockPos betterbees$getMemorizedHome();

    void betterbees$setMemorizedHome(BlockPos pos);

    default void betterbees$dropHive(Bee bee) {
        betterbees$removeMemorizedHive(bee);
        bee.getBrain().setMemory(ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get(), 200);
    }

    default void betterbees$dropAndBlacklistHive(Bee bee) {
        BlockPos home = betterbees$getMemorizedHome();
        if (home != null) {
            betterbees$blacklist(bee, home);
        }
        betterbees$dropHive(bee);
    }

    default void betterbees$blacklist(Bee bee, BlockPos pos) {
        GlobalPos target = GlobalPos.of(bee.level().dimension(), pos);
        List<GlobalPos> list = new ArrayList<>(bee.getBrain()
                .getMemory(ModMemoryTypes.HIVE_BLACKLIST.get())
                .orElseGet(List::of));
        if (!list.contains(target)) {
            list.add(target);
        }
        while (list.size() > 10) {
            list.remove(0);
        }
        bee.getBrain().setMemory(ModMemoryTypes.HIVE_BLACKLIST.get(), list);
    }

    default void betterbees$removeMemorizedHive(Bee bee) {
        bee.getBrain().setMemory(ModMemoryTypes.STUCK_TICKS.get(), 0);
        bee.getBrain().setMemory(ModMemoryTypes.TRAVELLING_TICKS.get(), 0);
        betterbees$setMemorizedHome(null);
    }
}
