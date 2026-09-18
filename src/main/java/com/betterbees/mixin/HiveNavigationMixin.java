package com.betterbees.mixin;

import com.betterbees.ai.NavigationBudget;
import com.betterbees.ai.HivePathScheduler;
import com.betterbees.ai.HiveRecalculationAccess;
import net.minecraft.server.level.ServerLevel;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.util.HiveMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block updates can trigger vanilla path recalculation outside the AI's moveTo request. */
@Mixin(PathNavigation.class)
public abstract class HiveNavigationMixin implements HiveRecalculationAccess {
    @Shadow @Final protected Mob mob;
    @Shadow protected long timeLastRecompute;
    @Shadow protected boolean hasDelayedRecomputation;
    @Unique private boolean betterbees$executingHiveRecalculation;

    @Inject(method = "recomputePath", at = @At("HEAD"), cancellable = true)
    private void betterbees$queueHiveRecalculation(CallbackInfo ci) {
        PathNavigation navigation = (PathNavigation) (Object) this;
        if (!betterbees$executingHiveRecalculation && mob instanceof Bee bee
                && bee.level() instanceof ServerLevel level
                && level.getGameTime() - timeLastRecompute > 20L
                && betterbees$isHivePath(bee, navigation.getTargetPos())) {
            HivePathScheduler.get(level).request(bee, navigation.getTargetPos(), true);
            hasDelayedRecomputation = false;
            ci.cancel();
        }
    }

    @Override
    public void betterbees$recomputeHivePathNow() {
        betterbees$executingHiveRecalculation = true;
        try {
            ((PathNavigation) (Object) this).recomputePath();
        } finally {
            betterbees$executingHiveRecalculation = false;
        }
    }

    @Redirect(method = "recomputePath", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;"))
    private Path betterbees$scopeHiveRecalculation(PathNavigation navigation, BlockPos target, int accuracy) {
        if (betterbees$executingHiveRecalculation) {
            return NavigationBudget.createPath(navigation, 10.0F, target, accuracy);
        }
        return navigation.createPath(target, accuracy);
    }

    @Unique
    private static boolean betterbees$isHivePath(Bee bee, BlockPos target) {
        return target != null && target.equals(((HiveMemory) bee).betterbees$getMemorizedHome())
                && bee.getBrain().isActive(Activity.IDLE)
                && bee.getBrain().getMemory(ModMemoryTypes.WANTS_HIVE.get()).orElse(false)
                && !bee.getBrain().hasMemoryValue(ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get())
                && !bee.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                && !bee.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !bee.getBrain().hasMemoryValue(MemoryModuleType.TEMPTING_PLAYER)
                && (bee.getLeashHolder() == null || target.closerToCenterThan(bee.getLeashHolder().position(), 5.5D));
    }

}
