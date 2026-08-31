package com.betterbees.mixin;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {
    @Accessor("maxVisitedNodesMultiplier")
    float betterbees$getMaxVisitedNodesMultiplier();
}
