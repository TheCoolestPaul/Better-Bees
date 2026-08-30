package com.betterbees.mixin;

import com.betterbees.hive.HiveHoneyStorage;
import com.betterbees.registry.ModDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeehiveBlockEntity.class)
public abstract class BeehivePersistenceMixin extends BlockEntity {
    private static final String HONEY_TAG = "BetterBeesHoney";

    protected BeehivePersistenceMixin(BlockPos pos, BlockState state) {
        super(BlockEntityType.BEEHIVE, pos, state);
    }

    @Inject(method = "loadAdditional", at = @At("HEAD"))
    private void betterbees$beginNbtLoad(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        ((HiveHoneyStorage) this).betterbees$setLoadingOccupants(true);
    }

    @Inject(method = "loadAdditional", at = @At("RETURN"))
    private void betterbees$finishNbtLoad(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        HiveHoneyStorage storage = (HiveHoneyStorage) this;
        storage.betterbees$setLoadingOccupants(false);
        BeehiveBlockEntity hive = (BeehiveBlockEntity) (Object) this;
        int honey = tag.contains(HONEY_TAG) ? tag.getInt(HONEY_TAG)
                : hive.getBlockState().hasProperty(BeehiveBlock.HONEY_LEVEL)
                ? hive.getBlockState().getValue(BeehiveBlock.HONEY_LEVEL) : 0;
        storage.betterbees$restoreHoney(honey);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void betterbees$saveHoney(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putInt(HONEY_TAG, ((HiveHoneyStorage) this).betterbees$getHoney());
    }

    @Inject(method = "applyImplicitComponents", at = @At("HEAD"))
    private void betterbees$beginComponentLoad(CallbackInfo ci) {
        ((HiveHoneyStorage) this).betterbees$setLoadingOccupants(true);
    }

    @Inject(method = "applyImplicitComponents", at = @At("RETURN"))
    private void betterbees$finishComponentLoad(BlockEntity.DataComponentInput input, CallbackInfo ci) {
        HiveHoneyStorage storage = (HiveHoneyStorage) this;
        storage.betterbees$setLoadingOccupants(false);
        Integer honey = input.get(ModDataComponents.HONEY.get());
        if (honey != null) storage.betterbees$restoreHoney(honey);
    }

    @Inject(method = "collectImplicitComponents", at = @At("TAIL"))
    private void betterbees$collectHoney(DataComponentMap.Builder components, CallbackInfo ci) {
        components.set(ModDataComponents.HONEY.get(), ((HiveHoneyStorage) this).betterbees$getHoney());
    }

    @Inject(method = "removeComponentsFromTag", at = @At("TAIL"))
    private void betterbees$removeHoneyTag(CompoundTag tag, CallbackInfo ci) {
        tag.remove(HONEY_TAG);
    }
}
