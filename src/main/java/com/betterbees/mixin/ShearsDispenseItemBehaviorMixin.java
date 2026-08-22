package com.betterbees.mixin;

import com.betterbees.hive.HiveHoneyService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.ShearsDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShearsDispenseItemBehavior.class)
public abstract class ShearsDispenseItemBehaviorMixin {
    @Inject(method = "tryShearBeehive", at = @At("HEAD"), cancellable = true)
    private static void betterbees$incrementalDispenserHarvest(ServerLevel level, BlockPos pos,
                                                                CallbackInfoReturnable<Boolean> cir) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.BEEHIVES) || !(state.getBlock() instanceof BeehiveBlock)
                || !(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive)) return;
        if (!HiveHoneyService.canHarvest(hive)) {
            cir.setReturnValue(false);
            return;
        }
        level.playSound(null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
        BeehiveBlock.popResource(level, pos, HiveHoneyService.randomHoneycomb(level.random));
        HiveHoneyService.consume(hive);
        hive.emptyAllLivingFromHive(null, level.getBlockState(pos), BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
        level.gameEvent(null, GameEvent.SHEAR, pos);
        cir.setReturnValue(true);
    }
}
