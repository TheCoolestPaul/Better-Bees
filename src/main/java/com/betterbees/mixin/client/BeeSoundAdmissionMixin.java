package com.betterbees.mixin.client;

import com.betterbees.client.BeeAudioController;
import net.minecraft.client.resources.sounds.BeeSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeeSoundInstance.class)
public abstract class BeeSoundAdmissionMixin {
    @Inject(method = "canPlaySound", at = @At("RETURN"), cancellable = true)
    private void betterbees$rejectRevokedLoop(CallbackInfoReturnable<Boolean> cir) {
        // SoundEngine drains its own queue, bypassing SoundManager.play. Recheck ownership there too.
        if (cir.getReturnValue() && !BeeAudioController.allow((BeeSoundInstance) (Object) this)) cir.setReturnValue(false);
    }
}
