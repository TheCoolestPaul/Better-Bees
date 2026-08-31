package com.betterbees.validation;

import net.minecraft.world.entity.animal.Bee;

/** Version adapters for sensing regressions and benchmark fixtures, not gameplay decisions. */
public final class SensingVersionHooks {
    private SensingVersionHooks() {}
    public static void setAnger(Bee bee, int ticks) { bee.setRemainingPersistentAngerTime(ticks); }
    public static void setAngerTarget(Bee bee, Bee target) { bee.setPersistentAngerTarget(target.getUUID()); }
    public static net.minecraft.world.entity.LivingEntity createNonBee(net.minecraft.server.level.ServerLevel level) {
        return net.minecraft.world.entity.EntityType.VILLAGER.create(level);
    }
}
