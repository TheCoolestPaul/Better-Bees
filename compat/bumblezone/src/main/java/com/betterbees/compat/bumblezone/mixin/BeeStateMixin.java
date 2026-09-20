package com.betterbees.compat.bumblezone.mixin;

import com.betterbees.compat.bumblezone.HeadwearState;
import com.telepathicgrunt.the_bumblezone.entities.goals.BeeFlowerHeadwearTemptGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Bee.class)
public abstract class BeeStateMixin implements HeadwearState {
    @Unique private LivingEntity betterbees$wearer;
    @Override public LivingEntity betterbees$headwearTarget() { return betterbees$wearer; }
    @Override public void betterbees$headwearTarget(LivingEntity target) { betterbees$wearer = target; }
    @Inject(method = "<init>", at = @At("TAIL"))
    private void betterbees$removeHeadwearGoal(EntityType<? extends Bee> type, Level level, CallbackInfo ci) {
        ((Bee) (Object) this).removeAllGoals(goal -> goal instanceof BeeFlowerHeadwearTemptGoal);
    }
}
