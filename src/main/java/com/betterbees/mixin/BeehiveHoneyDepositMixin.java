package com.betterbees.mixin;

import com.betterbees.hive.HiveHoneyService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BeehiveBlockEntity.class)
public abstract class BeehiveHoneyDepositMixin {
    @Redirect(
            method = "releaseOccupant",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BeehiveBlockEntity;getHoneyLevel(Lnet/minecraft/world/level/block/state/BlockState;)I")
    )
    private static int betterbees$allowDepositsPastVanillaFull(BlockState state) {
        return Math.min(4, state.getValue(BeehiveBlock.HONEY_LEVEL));
    }

    @Redirect(
            method = "releaseOccupant",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z")
    )
    private static boolean betterbees$storeDepositedHoney(Level level, BlockPos pos, BlockState proposedState) {
        // Vanilla has already rolled its capped 0-5 state update here. Roll the same
        // 1% bonus independently so it remains available even when the display proxy
        // is 4 or 5 while authoritative storage still has room.
        int amount = level.random.nextInt(100) == 0 ? 2 : 1;
        HiveHoneyService.add(level, pos, amount);
        return true;
    }
}
