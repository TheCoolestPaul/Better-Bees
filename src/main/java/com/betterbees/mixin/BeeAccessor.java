package com.betterbees.mixin;

import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Bee.class)
public interface BeeAccessor {
    @Accessor("stayOutOfHiveCountdown")
    int betterbees$getStayOutOfHiveCountdown();

    @Accessor("remainingCooldownBeforeLocatingNewFlower")
    void betterbees$setRemainingFlowerCooldown(int ticks);

    @Invoker("getCropsGrownSincePollination")
    int betterbees$getCropsGrownSincePollination();

    @Invoker("incrementNumCropsGrownSincePollination")
    void betterbees$incrementCropsGrownSincePollination();

    @Invoker("resetNumCropsGrownSincePollination")
    void betterbees$resetCropsGrownSincePollination();

    @Invoker("setHasNectar")
    void betterbees$setHasNectar(boolean hasNectar);
}
