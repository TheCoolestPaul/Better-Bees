package com.betterbees.mixin.client;

import com.betterbees.client.BeeAudioController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientBeeAudioMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void betterbees$tickBeeAudio(CallbackInfo ci) {
        BeeAudioController.tick((Minecraft) (Object) this);
    }
}
