package com.betterbees.mixin;

import com.betterbees.ai.NavigationBudget;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Block updates can trigger vanilla path recalculation outside the AI's moveTo request. */
@Mixin(PathNavigation.class)
public abstract class HiveNavigationMixin {
    @Shadow @Final protected Mob mob;

    @Redirect(method = "recomputePath", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;"))
    private Path betterbees$scopeHiveRecalculation(PathNavigation navigation, BlockPos target, int accuracy) {
        if (mob instanceof Bee bee && target.equals(((HiveMemory) bee).betterbees$getMemorizedHome())
                && bee.getBrain().isActive(Activity.IDLE)
                && bee.getBrain().getMemory(ModMemoryTypes.WANTS_HIVE.get()).orElse(false)
                && !bee.getBrain().hasMemoryValue(ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get())
                && !bee.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                && !bee.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !bee.getBrain().hasMemoryValue(MemoryModuleType.TEMPTING_PLAYER)
                && (bee.getLeashHolder() == null || target.closerToCenterThan(bee.getLeashHolder().position(), 5.5D))) {
            return NavigationBudget.createPath(navigation, 10.0F, target, accuracy);
        }
        return navigation.createPath(target, accuracy);
    }
}
