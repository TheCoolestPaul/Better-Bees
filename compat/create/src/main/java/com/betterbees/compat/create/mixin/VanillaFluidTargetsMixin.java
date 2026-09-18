package com.betterbees.compat.create.mixin;

import com.betterbees.hive.HiveHoneyService;
import com.simibubi.create.AllFluids;
import com.simibubi.create.content.fluids.pipes.VanillaFluidTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VanillaFluidTargets.class, remap = false)
public abstract class VanillaFluidTargetsMixin {
    @Inject(method = "drainBlock", at = @At("HEAD"), cancellable = true)
    private static void betterbees$drainHoney(Level level, BlockPos pos, BlockState state, boolean simulate,
                                              CallbackInfoReturnable<FluidStack> cir) {
        if (!(state.getBlock() instanceof BeehiveBlock)
                || !(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive)) return;
        boolean available = simulate ? HiveHoneyService.canHarvest(hive) : HiveHoneyService.consume(hive);
        cir.setReturnValue(available ? new FluidStack(AllFluids.HONEY.get().getSource(), 250) : FluidStack.EMPTY);
    }
}
