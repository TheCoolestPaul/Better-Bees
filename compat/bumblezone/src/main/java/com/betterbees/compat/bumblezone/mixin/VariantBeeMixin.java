package com.betterbees.compat.bumblezone.mixin;

import com.betterbees.ai.BeeAi;
import com.betterbees.util.BeeScaleService;
import com.telepathicgrunt.the_bumblezone.entities.mobs.VariantBeeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VariantBeeEntity.class)
public abstract class VariantBeeMixin {
    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void betterbees$brainBreeding(CallbackInfo ci) {
        ((Bee) (Object) this).removeAllGoals(goal -> goal instanceof BreedGoal);
    }
    @Inject(method = "getBreedOffspring", at = @At("RETURN"))
    private void betterbees$child(ServerLevel level, AgeableMob partner, CallbackInfoReturnable<Bee> cir) {
        Bee child = cir.getReturnValue();
        if (child != null) {
            BeeAi.initMemories(child, child.getRandom());
            BeeScaleService.apply(child);
        }
    }
}
