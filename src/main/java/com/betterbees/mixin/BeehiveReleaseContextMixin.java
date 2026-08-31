package com.betterbees.mixin;

import com.betterbees.hive.HiveTransitionSounds;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

@Mixin(BeehiveBlockEntity.class)
public abstract class BeehiveReleaseContextMixin {
    @Redirect(method = "emptyAllLivingFromHive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BeehiveBlockEntity;releaseAllOccupants(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BeehiveBlockEntity$BeeReleaseStatus;)Ljava/util/List;"))
    private List<Entity> betterbees$keepEvacuationSoundOwner(BeehiveBlockEntity hive, BlockState state,
            BeehiveBlockEntity.BeeReleaseStatus status) {
        return HiveTransitionSounds.duringRelease(hive,
                () -> ((BeehiveAccessor) hive).betterbees$releaseAllOccupants(state, status));
    }
}
