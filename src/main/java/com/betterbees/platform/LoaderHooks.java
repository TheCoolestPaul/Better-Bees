package com.betterbees.platform;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ItemAbilities;

public final class LoaderHooks {
    private LoaderHooks() {}

    public static java.nio.file.Path configDirectory() { return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get(); }

    public static boolean isShears(ItemStack stack) {
        return stack.canPerformAction(ItemAbilities.SHEARS_HARVEST);
    }
}
