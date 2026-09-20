package com.betterbees.compat.bumblezone.mixin;

import com.betterbees.compat.BeeCompatibility;
import com.betterbees.compat.bumblezone.BumblezoneBrain;
import com.telepathicgrunt.the_bumblezone.enchantments.CombCutterEnchantmentApplication;
import com.telepathicgrunt.the_bumblezone.items.essence.EssenceOfTheBees;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BeeCompatibility.class, remap = false)
public abstract class CompatibilityMixin {
    @Inject(method = "initialize", at = @At("TAIL"))
    private static void betterbees$initialize(CallbackInfo ci) {
        org.slf4j.LoggerFactory.getLogger("BetterBees/Bumblezone").info("Better Bees Bumblezone integration enabled");
    }
    @Inject(method = "shear", at = @At("HEAD"))
    private static void betterbees$combCutter(ItemStack tool, Player player, Level level, BlockPos pos, CallbackInfo ci) {
        CombCutterEnchantmentApplication.increasedCombDrops(tool, player, level, pos);
    }
    @Inject(method = "protectedPlayer", at = @At("HEAD"), cancellable = true)
    private static void betterbees$essence(Player player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && EssenceOfTheBees.hasEssence(serverPlayer));
    }
    @Inject(method = "configureBrain", at = @At("TAIL"))
    private static void betterbees$configure(Brain<Bee> brain, CallbackInfo ci) { BumblezoneBrain.configure(brain); }
    @Inject(method = "beforeBrain", at = @At("HEAD"))
    private static void betterbees$before(Bee bee, CallbackInfo ci) { BumblezoneBrain.beforeBrain(bee); }
    @Inject(method = "selectActivity", at = @At("HEAD"), cancellable = true)
    private static void betterbees$select(Bee bee, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(BumblezoneBrain.selectActivity(bee));
    }
}
