package com.betterbees.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin {
    @Inject(method = "getPathTypeFromState", at = @At("RETURN"), cancellable = true)
    private static void betterbees$avoidLadders(
            BlockGetter level,
            BlockPos pos,
            CallbackInfoReturnable<PathType> cir
    ) {
        if (level.getBlockState(pos).is(Blocks.LADDER)) {
            cir.setReturnValue(PathType.TRAPDOOR);
        }
    }
}
