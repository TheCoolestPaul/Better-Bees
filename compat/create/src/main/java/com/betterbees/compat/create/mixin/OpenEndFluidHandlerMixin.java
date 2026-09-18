package com.betterbees.compat.create.mixin;

import com.simibubi.create.content.fluids.OpenEndedPipe;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep Create's pipe buffer, but make hive batch transfers conservative and filter-safe. */
@Mixin(targets = "com.simibubi.create.content.fluids.OpenEndedPipe$OpenEndFluidHandler", remap = false)
public abstract class OpenEndFluidHandlerMixin extends FluidTank {
    @Shadow @Final private OpenEndedPipe this$0;

    protected OpenEndFluidHandlerMixin(int capacity) { super(capacity); }

    @Inject(method = "drainInner", at = @At("HEAD"), cancellable = true)
    private void betterbees$drainHive(int amount, FluidStack filter, FluidAction action, CallbackInfoReturnable<FluidStack> cir) {
        var world = this$0.getWorld();
        var pos = this$0.getOutputPos();
        if (world == null || !world.isLoaded(pos)
                || !(world.getBlockState(pos).getBlock() instanceof BeehiveBlock)
                || !(world.getBlockEntity(pos) instanceof BeehiveBlockEntity)) return;
        cir.setReturnValue(FluidStack.EMPTY);
        if (amount <= 0) return;
        if (action.execute()) ((OpenEndedPipeAccessor) this$0).betterbees$setPulling(true);
        if (!getFluid().isEmpty()) {
            cir.setReturnValue(filter == null ? super.drain(amount, action) : super.drain(filter, action));
            return;
        }
        var access = (OpenEndedPipeAccessor) this$0;
        FluidStack batch = access.betterbees$removeFluid(true);
        if (batch.isEmpty() || (filter != null && !FluidStack.isSameFluidSameComponents(batch, filter))) return;
        if (action.execute()) batch = access.betterbees$removeFluid(false);
        if (batch.isEmpty()) return;
        int drained = Math.min(amount, batch.getAmount());
        FluidStack result = batch.copyWithAmount(drained);
        if (action.execute() && drained < batch.getAmount()) {
            super.fill(batch.copyWithAmount(batch.getAmount() - drained), FluidAction.EXECUTE);
        }
        cir.setReturnValue(result);
    }
}
