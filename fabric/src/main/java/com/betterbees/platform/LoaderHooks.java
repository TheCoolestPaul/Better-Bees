package com.betterbees.platform;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;

public final class LoaderHooks {
    private LoaderHooks() {}

    public static java.nio.file.Path configDirectory() { return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir(); }

    public static boolean isShears(ItemStack stack) {
        return stack.getItem() instanceof ShearsItem;
    }
}
