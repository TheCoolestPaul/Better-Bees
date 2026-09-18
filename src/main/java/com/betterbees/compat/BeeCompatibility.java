package com.betterbees.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Optional adapters inject here; the normal artifacts never link optional mod classes. */
public final class BeeCompatibility {
    private BeeCompatibility() {}
    public static void initialize() {}
    public static void shear(ItemStack tool, Player player, Level level, BlockPos pos) {}
    public static boolean protectedPlayer(Player player) { return false; }
    public static void configureBrain(Brain<Bee> brain) {}
    public static void beforeBrain(Bee bee) {}
    public static boolean selectActivity(Bee bee) { return false; }
}
