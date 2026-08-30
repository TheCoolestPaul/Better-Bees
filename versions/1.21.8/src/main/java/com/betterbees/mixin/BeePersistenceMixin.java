package com.betterbees.mixin;

import com.betterbees.util.BeePersistentState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Bee.class)
public abstract class BeePersistenceMixin {
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void betterbees$save(ValueOutput output, CallbackInfo ci) {
        BeePersistentState state = (BeePersistentState) this;
        output.putInt("BetterBeesHoneyCooldown", state.betterbees$getHoneyCooldown());
        output.storeNullable("BetterBeesMemorizedHome", BlockPos.CODEC, state.betterbees$getMemorizedHome());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void betterbees$load(ValueInput input, CallbackInfo ci) {
        BeePersistentState state = (BeePersistentState) this;
        state.betterbees$setHoneyCooldown(input.getIntOr("BetterBeesHoneyCooldown", 0));
        input.read("BetterBeesMemorizedHome", BlockPos.CODEC).ifPresent(state::betterbees$restoreMemorizedHome);
        state.betterbees$finishPersistentLoad();
    }
}
