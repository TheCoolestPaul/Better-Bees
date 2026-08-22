package com.betterbees.mixin;

import com.betterbees.config.BetterBeesConfig;
import com.betterbees.hive.HiveBreedingService;
import com.betterbees.hive.HiveHoneyService;
import com.betterbees.hive.HiveHoneyStorage;
import com.betterbees.hive.HiveFlowerIndex;
import com.betterbees.hive.HiveFlowerKnowledge;
import com.betterbees.hive.HiveFlowerService;
import com.betterbees.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeehiveBlockEntity.class)
public abstract class BeehiveBlockEntityMixin extends BlockEntity implements HiveHoneyStorage, HiveFlowerKnowledge {
    @Unique private static final String BETTERBEES_HONEY_TAG = "BetterBeesHoney";
    @Unique private boolean betterbees$loadingOccupants;
    @Unique private int betterbees$honey = -1;
    @Unique private final HiveFlowerIndex betterbees$flowerIndex = new HiveFlowerIndex();

    protected BeehiveBlockEntityMixin(BlockPos pos, BlockState state) {
        super(BlockEntityType.BEEHIVE, pos, state);
    }

    @Override
    public int betterbees$getHoney() {
        if (betterbees$honey < 0) {
            BeehiveBlockEntity hive = (BeehiveBlockEntity) (Object) this;
            BlockState state = hive.getBlockState();
            betterbees$honey = state.hasProperty(BeehiveBlock.HONEY_LEVEL)
                    ? state.getValue(BeehiveBlock.HONEY_LEVEL)
                    : 0;
        }
        return betterbees$honey;
    }

    @Override
    public HiveFlowerIndex betterbees$getFlowerIndex() {
        return betterbees$flowerIndex;
    }

    @Override
    public void betterbees$setHoney(int honey) {
        betterbees$honey = Math.max(0, honey);
        ((BeehiveBlockEntity) (Object) this).setChanged();
    }

    @Inject(method = "isFull", at = @At("HEAD"), cancellable = true)
    private void betterbees$useConfiguredCapacity(CallbackInfoReturnable<Boolean> cir) {
        BeehiveBlockEntity hive = (BeehiveBlockEntity) (Object) this;
        cir.setReturnValue(hive.getOccupantCount() >= BetterBeesConfig.hiveCapacity());
    }

    @Inject(method = "addOccupant", at = @At("HEAD"), cancellable = true)
    private void betterbees$guardLiveEntry(Entity occupant, CallbackInfo ci) {
        BeehiveBlockEntity hive = (BeehiveBlockEntity) (Object) this;
        if (hive.getOccupantCount() >= BetterBeesConfig.hiveCapacity()) ci.cancel();
    }

    @ModifyConstant(method = "addOccupant", constant = @Constant(intValue = 3))
    private int betterbees$replaceVanillaCapacity(int vanillaCapacity) {
        return BetterBeesConfig.hiveCapacity();
    }

    @Inject(method = "storeBee", at = @At("HEAD"), cancellable = true)
    private void betterbees$guardStoredEntry(BeehiveBlockEntity.Occupant occupant, CallbackInfo ci) {
        BeehiveBlockEntity hive = (BeehiveBlockEntity) (Object) this;
        if (!betterbees$loadingOccupants && hive.getOccupantCount() >= BetterBeesConfig.hiveCapacity()) ci.cancel();
    }

    @Inject(method = "loadAdditional", at = @At("HEAD"))
    private void betterbees$beginNbtLoad(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        betterbees$loadingOccupants = true;
    }

    @Inject(method = "loadAdditional", at = @At("RETURN"))
    private void betterbees$finishNbtLoad(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        betterbees$loadingOccupants = false;
        BeehiveBlockEntity hive = (BeehiveBlockEntity) (Object) this;
        betterbees$honey = tag.contains(BETTERBEES_HONEY_TAG)
                ? Math.max(0, tag.getInt(BETTERBEES_HONEY_TAG))
                : (hive.getBlockState().hasProperty(BeehiveBlock.HONEY_LEVEL)
                    ? hive.getBlockState().getValue(BeehiveBlock.HONEY_LEVEL) : 0);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void betterbees$saveHoney(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putInt(BETTERBEES_HONEY_TAG, betterbees$getHoney());
    }

    @Inject(method = "applyImplicitComponents", at = @At("HEAD"))
    private void betterbees$beginComponentLoad(CallbackInfo ci) {
        betterbees$loadingOccupants = true;
    }

    @Inject(method = "applyImplicitComponents", at = @At("RETURN"))
    private void betterbees$finishComponentLoad(BlockEntity.DataComponentInput componentInput, CallbackInfo ci) {
        betterbees$loadingOccupants = false;
        Integer honey = componentInput.get(ModDataComponents.HONEY.get());
        if (honey != null) betterbees$honey = Math.max(0, honey);
    }

    @Inject(method = "collectImplicitComponents", at = @At("TAIL"))
    private void betterbees$collectHoney(DataComponentMap.Builder components, CallbackInfo ci) {
        components.set(ModDataComponents.HONEY.get(), betterbees$getHoney());
    }

    @Inject(method = "removeComponentsFromTag", at = @At("TAIL"))
    private void betterbees$removeHoneyTag(CompoundTag tag, CallbackInfo ci) {
        tag.remove(BETTERBEES_HONEY_TAG);
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void betterbees$tickIndoorBreeding(Level level, BlockPos pos, BlockState state,
                                                       BeehiveBlockEntity hive, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) {
            HiveBreedingService.tick(serverLevel, pos, hive);
            HiveFlowerService.tick(serverLevel, pos, hive);
        }
        HiveHoneyService.syncDisplay(hive);
    }
}
