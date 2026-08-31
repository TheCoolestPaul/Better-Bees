package com.betterbees.mixin.client;

import com.betterbees.client.BeeAudioController;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class BeeSoundManagerMixin {
    @Inject(method = "queueTickingSound", at = @At("HEAD"), cancellable = true)
    private void betterbees$admitQueuedBeeLoop(TickableSoundInstance sound, CallbackInfo ci) {
        if (!BeeAudioController.allow(sound)) ci.cancel();
    }

    @Inject(method = {"apply", "reload"}, at = @At("HEAD"))
    private void betterbees$resetReloadedAudio(CallbackInfo ci) {
        BeeAudioController.reset((SoundManager) (Object) this);
    }
}
