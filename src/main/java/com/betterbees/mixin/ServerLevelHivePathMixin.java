package com.betterbees.mixin;

import com.betterbees.ai.HivePathScheduler;
import com.betterbees.ai.HivePathSchedulerAccess;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelHivePathMixin implements HivePathSchedulerAccess {
    @Unique private final HivePathScheduler betterbees$hivePaths = new HivePathScheduler();

    @Override public HivePathScheduler betterbees$getHivePathScheduler() { return betterbees$hivePaths; }

    @Inject(method = "tick", at = @At("TAIL"))
    private void betterbees$drainHivePaths(CallbackInfo ci) {
        betterbees$hivePaths.tick((ServerLevel) (Object) this);
    }
}
