package com.betterbees.platform;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ItemAbilities;

public final class LoaderHooks {
    private LoaderHooks() {}

    public static boolean isShears(ItemStack stack) {
        return stack.canPerformAction(ItemAbilities.SHEARS_HARVEST);
    }
}
