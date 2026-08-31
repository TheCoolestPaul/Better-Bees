package com.betterbees.mixin;

import com.betterbees.config.BetterBeesConfig;
import com.betterbees.hive.HiveRuntimeAccess;
import com.betterbees.hive.HiveTransitionSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Minecraft 1.21.8+ sound calls use Entity instead of Player as the excluded listener. */
@Mixin(BeehiveBlockEntity.class)
public abstract class BeehiveSoundMixin {
    @Redirect(method = "addOccupant", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private void betterbees$limitEntry(Level level, Entity player, double x, double y, double z,
                                      SoundEvent sound, SoundSource source, float volume, float pitch) {
        if (((HiveRuntimeAccess) this).betterbees$getRuntimeState()
                .allowTransitionSound(level.getGameTime(), BetterBeesConfig.hiveTransitionIntervalTicks())) {
            level.playSound(player, x, y, z, sound, source, volume, pitch);
        }
    }

    @Redirect(method = "releaseOccupant", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private static void betterbees$limitExit(Level level, Entity player, BlockPos pos,
                                           SoundEvent sound, SoundSource source, float volume, float pitch) {
        if (HiveTransitionSounds.allowExit(level, pos)) {
            level.playSound(player, pos, sound, source, volume, pitch);
        }
    }
}
