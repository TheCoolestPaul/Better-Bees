package com.betterbees.platform;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;

public final class LoaderHooks {
    private LoaderHooks() {}

    public static boolean isShears(ItemStack stack) {
        return stack.getItem() instanceof ShearsItem;
    }
}
