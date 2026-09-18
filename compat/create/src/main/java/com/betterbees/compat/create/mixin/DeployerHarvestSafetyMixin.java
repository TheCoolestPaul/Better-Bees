package com.betterbees.compat.create.mixin;

import com.betterbees.hive.HiveHarvestSafety;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HiveHarvestSafety.class, remap = false)
public abstract class DeployerHarvestSafetyMixin {
    @Inject(method = "isSafe", at = @At("HEAD"), cancellable = true)
    private static void betterbees$safeDeployer(Level level, BlockPos pos, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof DeployerFakePlayer) cir.setReturnValue(true);
    }
}
