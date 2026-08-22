package com.betterbees.mixin;

import com.betterbees.ai.BeeAi;
import com.betterbees.registry.ModMemoryTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeehiveBlockEntity.Occupant.class)
public abstract class BeehiveOccupantMixin {
    @Inject(method = "createEntity", at = @At("RETURN"))
    private void betterbees$initializeStoredBee(Level level, BlockPos pos, CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() instanceof Bee bee
                && !bee.getBrain().hasMemoryValue(ModMemoryTypes.POLLINATING_COOLDOWN.get())) {
            BeeAi.initMemories(bee, bee.getRandom());
        }
    }
}
