package com.betterbees.mixin;

import com.betterbees.hive.HoneyBottleDispenseBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DispenserBlock.class)
public abstract class DispenserBottleMixin {
    @Inject(method = "getDispenseMethod", at = @At("RETURN"), cancellable = true)
    private void betterbees$bottleBehavior(Level level, ItemStack stack, CallbackInfoReturnable<DispenseItemBehavior> cir) {
        if ((Object) this instanceof DropperBlock || !stack.is(Items.GLASS_BOTTLE)) return;
        DispenseItemBehavior behavior = cir.getReturnValue();
        if (behavior != DispenseItemBehavior.NOOP && !(behavior instanceof HoneyBottleDispenseBehavior)) {
            cir.setReturnValue(new HoneyBottleDispenseBehavior(behavior));
        }
    }
}
