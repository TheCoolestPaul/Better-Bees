package com.betterbees.compat.bumblezone.mixin;

import com.telepathicgrunt.the_bumblezone.entities.mobs.VariantBeeEntity;
import net.minecraft.world.entity.ai.behavior.AnimalMakeLove;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(AnimalMakeLove.class)
public abstract class BreedingMixin {
    @Unique private static boolean betterbees$supported(Animal animal) {
        return animal.getClass() == Bee.class || animal instanceof VariantBeeEntity;
    }
    @Inject(method = "findValidBreedPartner", at = @At("HEAD"), cancellable = true)
    private void betterbees$partner(Animal owner, CallbackInfoReturnable<Optional<? extends Animal>> cir) {
        if (!betterbees$supported(owner)) return;
        cir.setReturnValue(owner.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .flatMap(visible -> visible.findClosest(entity -> entity instanceof Animal animal
                        && betterbees$supported(animal) && owner.canMate(animal) && !animal.isPanicking()))
                .map(Animal.class::cast));
    }
    @Inject(method = "hasBreedTargetOfRightType", at = @At("HEAD"), cancellable = true)
    private void betterbees$partnerType(Animal owner, CallbackInfoReturnable<Boolean> cir) {
        if (!betterbees$supported(owner)) return;
        cir.setReturnValue(owner.getBrain().getMemory(MemoryModuleType.BREED_TARGET)
                .filter(entity -> entity instanceof Animal animal && betterbees$supported(animal)).isPresent());
    }
}
