package com.betterbees.compat.bumblezone.mixin;

import com.betterbees.hive.HiveTransitionSounds;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BeehiveBlockEntity.class)
public abstract class BumblezoneReleaseContextMixin {
    // Leave the releaseAllOccupants invocation intact for Bumblezone's local-capture injection.
    @WrapMethod(method = "emptyAllLivingFromHive")
    private void betterbees$releaseContext(Player player, BlockState state,
            BeehiveBlockEntity.BeeReleaseStatus status, Operation<Void> original) {
        HiveTransitionSounds.duringRelease((BeehiveBlockEntity) (Object) this, () -> {
            original.call(player, state, status);
            return null;
        });
    }
}
