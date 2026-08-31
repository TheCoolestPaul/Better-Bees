package com.betterbees.mixin;

import com.betterbees.hive.HiveRuntimeState;
import com.betterbees.hive.HiveRuntimeAccess;
import com.betterbees.config.BetterBeesConfig;
import com.betterbees.hive.HiveBreedingService;
import com.betterbees.hive.HiveHoneyService;
import com.betterbees.hive.HiveHoneyStorage;
import com.betterbees.hive.HiveFlowerIndex;
import com.betterbees.hive.HiveFlowerKnowledge;
import com.betterbees.hive.HiveFlowerService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
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
public abstract class BeehiveBlockEntityMixin extends BlockEntity implements HiveHoneyStorage, HiveFlowerKnowledge, HiveRuntimeAccess {
    @Unique private final HiveRuntimeState betterbees$runtimeState = new HiveRuntimeState();

    @Override
    public HiveRuntimeState betterbees$getRuntimeState() { return betterbees$runtimeState; }
    @Unique private static final String BETTERBEES_HONEY_TAG = "BetterBeesHoney";
    @Unique private boolean betterbees$loadingOccupants;
    @Unique private int betterbees$honey = -1;
    @Unique private boolean betterbees$honeyDisplayDirty = true;
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
        betterbees$honeyDisplayDirty = true;
        ((BeehiveBlockEntity) (Object) this).setChanged();
    }

    @Override
    public boolean betterbees$isHoneyDisplayDirty() {
        return betterbees$honeyDisplayDirty;
    }

    @Override
    public void betterbees$markHoneyDisplaySynced() {
        betterbees$honeyDisplayDirty = false;
    }

    @Override
    public void betterbees$setLoadingOccupants(boolean loading) {
        betterbees$loadingOccupants = loading;
    }

    @Override
    public void betterbees$restoreHoney(int honey) {
        betterbees$honey = Math.max(0, honey);
        betterbees$honeyDisplayDirty = true;
    }

    @Inject(method = "isFull", at = @At("HEAD"), cancellable = true)
    private void betterbees$useConfiguredCapacity(CallbackInfoReturnable<Boolean> cir) {
        BeehiveBlockEntity hive = (BeehiveBlockEntity) (Object) this;
        cir.setReturnValue(hive.getOccupantCount() >= BetterBeesConfig.hiveCapacity());
    }

    @Inject(method = "addOccupant", at = @At("HEAD"), cancellable = true)
    private void betterbees$guardLiveEntry(CallbackInfo ci) {
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

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void betterbees$tickIndoorBreeding(Level level, BlockPos pos, BlockState state,
                                                       BeehiveBlockEntity hive, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) {
            HiveBreedingService.tick(serverLevel, pos, hive);
            HiveFlowerService.tick(serverLevel, pos, hive);
        }
        if (((HiveHoneyStorage) hive).betterbees$isHoneyDisplayDirty()) {
            HiveHoneyService.syncDisplay(hive);
        }
    }
}
