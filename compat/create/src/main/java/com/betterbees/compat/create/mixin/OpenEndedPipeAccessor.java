package com.betterbees.compat.create.mixin;

import com.simibubi.create.content.fluids.OpenEndedPipe;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = OpenEndedPipe.class, remap = false)
public interface OpenEndedPipeAccessor {
    @Accessor("wasPulling") void betterbees$setPulling(boolean pulling);
    @Invoker("removeFluidFromSpace") FluidStack betterbees$removeFluid(boolean simulate);
}
