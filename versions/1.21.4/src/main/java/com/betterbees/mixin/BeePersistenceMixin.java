package com.betterbees.mixin;

import com.betterbees.util.BeePersistentState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Bee.class)
public abstract class BeePersistenceMixin {
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void betterbees$save(CompoundTag tag, CallbackInfo ci) {
        BeePersistentState state = (BeePersistentState) this;
        tag.putInt("BetterBeesHoneyCooldown", state.betterbees$getHoneyCooldown());
        BlockPos home = state.betterbees$getMemorizedHome();
        if (home != null) tag.put("BetterBeesMemorizedHome", NbtUtils.writeBlockPos(home));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void betterbees$load(CompoundTag tag, CallbackInfo ci) {
        BeePersistentState state = (BeePersistentState) this;
        state.betterbees$setHoneyCooldown(tag.getInt("BetterBeesHoneyCooldown"));
        NbtUtils.readBlockPos(tag, "BetterBeesMemorizedHome").ifPresent(state::betterbees$restoreMemorizedHome);
        state.betterbees$finishPersistentLoad();
    }
}
