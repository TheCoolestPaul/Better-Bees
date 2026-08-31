package com.betterbees.validation;

import net.minecraft.world.entity.animal.Bee;

/** Version adapters for sensing regressions, not gameplay decisions. */
public final class SensingVersionHooks {
    private SensingVersionHooks() {}
    public static void setAnger(Bee bee, int ticks) { bee.setTimeToRemainAngry(ticks); }
    public static void setAngerTarget(Bee bee, Bee target) {
        bee.setPersistentAngerTarget(net.minecraft.world.entity.EntityReference.of(target));
    }
    public static net.minecraft.world.entity.LivingEntity createNonBee(net.minecraft.server.level.ServerLevel level) {
        return net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
    }
}
