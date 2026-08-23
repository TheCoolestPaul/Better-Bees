package com.betterbees.mixin;

import com.betterbees.ai.BeeAi;
import com.betterbees.config.BetterBeesConfig;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.registry.ModSensorTypes;
import com.betterbees.util.BeeScaleService;
import com.betterbees.util.HiveMemory;
import com.betterbees.hive.HiveFlowerService;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.pathfinder.PathType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(Bee.class)
public abstract class BeeMixin extends Animal implements HiveMemory {
    @Unique
    private static ImmutableList<SensorType<? extends Sensor<? super Bee>>> betterbees$sensors() {
        return ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS,
                SensorType.HURT_BY, SensorType.NEAREST_ADULT,
                ModSensorTypes.BEE_TEMPTATIONS.get(), ModSensorTypes.BEE_MEMORIES.get());
    }

    @Unique
    private static ImmutableList<MemoryModuleType<?>> betterbees$memories() {
        return ImmutableList.of(
            MemoryModuleType.PATH, MemoryModuleType.BREED_TARGET, MemoryModuleType.NEAREST_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.LOOK_TARGET,
            MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ATTACK_COOLING_DOWN,
            MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.HURT_BY_ENTITY,
            MemoryModuleType.NEAREST_ATTACKABLE, MemoryModuleType.TEMPTING_PLAYER,
            MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, MemoryModuleType.IS_TEMPTED,
            MemoryModuleType.IS_PANICKING, ModMemoryTypes.FLOWER_POS.get(), ModMemoryTypes.LAST_PATH.get(),
            ModMemoryTypes.HIVE_BLACKLIST.get(), ModMemoryTypes.POLLINATING_COOLDOWN.get(),
            ModMemoryTypes.POLLINATING_TICKS.get(), ModMemoryTypes.SUCCESSFUL_POLLINATING_TICKS.get(),
            ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get(), ModMemoryTypes.TRAVELLING_TICKS.get(),
            ModMemoryTypes.SEARCH_ATTEMPTS.get(), ModMemoryTypes.STUCK_TICKS.get(), ModMemoryTypes.WANTS_HIVE.get());
    }

    @Unique private BlockPos betterbees$memorizedHome;
    @Unique private int betterbees$honeyCooldown;
    @Unique private boolean betterbees$scaleInitialized;
    @Shadow private @Nullable UUID persistentAngerTarget;
    @Shadow public abstract @Nullable BlockPos getHivePos();

    protected BeeMixin(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    @Override public BlockPos betterbees$getMemorizedHome() { return betterbees$memorizedHome; }
    @Override public void betterbees$setMemorizedHome(BlockPos pos) {
        if (betterbees$memorizedHome != null && !betterbees$memorizedHome.equals(pos)
                && level() instanceof ServerLevel serverLevel) {
            HiveFlowerService.release(serverLevel, betterbees$memorizedHome, (Bee) (Object) this);
        }
        betterbees$memorizedHome = pos;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void betterbees$configureNavigation(EntityType<? extends Bee> type, Level level, CallbackInfo ci) {
        setPathfindingMalus(PathType.TRAPDOOR, -1.0F);
        setPathfindingMalus(PathType.DOOR_IRON_CLOSED, -1.0F);
        setPathfindingMalus(PathType.DOOR_WOOD_CLOSED, -1.0F);
    }

    @Inject(method = "registerGoals", at = @At("RETURN"))
    private void betterbees$replaceGoals(CallbackInfo ci) {
        removeAllGoals(goal -> true);
    }

    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void betterbees$tickBrain(CallbackInfo ci) {
        Bee bee = (Bee) (Object) this;
        bee.setNoGravity(true);
        level().getProfiler().push("betterBeesBrain");
        getBrain().tick((ServerLevel) level(), bee);
        level().getProfiler().pop();
        BeeAi.updateActivity(bee);
        if (persistentAngerTarget != null && getTarget() != null) {
            getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, getTarget());
        }
        if (!getBrain().hasMemoryValue(ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get())) {
            getBrain().eraseMemory(ModMemoryTypes.HIVE_BLACKLIST.get());
        }
        boolean abandonedUnsafeHive = betterbees$memorizedHome != null
                && (!(level().getBlockEntity(betterbees$memorizedHome) instanceof BeehiveBlockEntity)
                || BeeAi.isHiveNearFire((ServerLevel) level(), bee));
        if (abandonedUnsafeHive) {
            betterbees$dropAndBlacklistHive(bee);
            getBrain().eraseMemory(ModMemoryTypes.WANTS_HIVE.get());
        }
        boolean tired = getBrain().getMemory(ModMemoryTypes.SEARCH_ATTEMPTS.get()).orElse(0)
                >= BetterBeesConfig.searchAttempts();
        boolean returnTrigger = level().isRaining() || level().isNight() || tired || bee.hasNectar();
        boolean preventsReturn = abandonedUnsafeHive
                || ((BeeAccessor) bee).betterbees$getStayOutOfHiveCountdown() > 0
                || bee.hasStung() || bee.getTarget() != null;
        if (preventsReturn) getBrain().eraseMemory(ModMemoryTypes.WANTS_HIVE.get());
        else if (returnTrigger) getBrain().setMemory(ModMemoryTypes.WANTS_HIVE.get(), true);
    }

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void betterbees$initializeScaleFallback(CallbackInfo ci) {
        if (!betterbees$scaleInitialized) {
            betterbees$scaleInitialized = BeeScaleService.apply((Bee) (Object) this);
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void betterbees$save(CompoundTag tag, CallbackInfo ci) {
        tag.putInt("BetterBeesHoneyCooldown", betterbees$honeyCooldown);
        if (betterbees$memorizedHome != null) tag.put("BetterBeesMemorizedHome", NbtUtils.writeBlockPos(betterbees$memorizedHome));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void betterbees$load(CompoundTag tag, CallbackInfo ci) {
        betterbees$honeyCooldown = tag.getInt("BetterBeesHoneyCooldown");
        NbtUtils.readBlockPos(tag, "BetterBeesMemorizedHome").ifPresent(pos -> betterbees$memorizedHome = pos);
        if (betterbees$memorizedHome == null) betterbees$memorizedHome = getHivePos();
        betterbees$scaleInitialized = BeeScaleService.apply((Bee) (Object) this);
    }

    @Inject(method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/animal/Bee;", at = @At("RETURN"))
    private void betterbees$initializeOffspring(ServerLevel level, AgeableMob partner, CallbackInfoReturnable<Bee> cir) {
        if (cir.getReturnValue() != null) {
            BeeAi.initMemories(cir.getReturnValue(), cir.getReturnValue().getRandom());
            BeeScaleService.apply(cir.getReturnValue());
        }
    }

    @Inject(method = "isFlapping", at = @At("RETURN"), cancellable = true)
    private void betterbees$flapInFlight(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(!onGround());
    }

    @ModifyArg(method = "createNavigation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/FlyingPathNavigation;setCanFloat(Z)V"))
    private boolean betterbees$allowFloating(boolean vanillaValue) {
        return true;
    }

    @Override public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos).isAir() ? 10.0F : 0.0F;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data) {
        BeeAi.initMemories((Bee) (Object) this, level.getRandom());
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        betterbees$scaleInitialized = BeeScaleService.apply((Bee) (Object) this);
        return result;
    }

    public Brain.Provider<Bee> brainProvider() { return Brain.provider(betterbees$memories(), betterbees$sensors()); }
    public Brain<?> makeBrain(Dynamic<?> dynamic) { return BeeAi.makeBrain(brainProvider().makeBrain(dynamic)); }
    @SuppressWarnings("unchecked")
    public Brain<Bee> getBrain() { return (Brain<Bee>) super.getBrain(); }
}
