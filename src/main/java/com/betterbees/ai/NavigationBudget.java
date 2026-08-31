package com.betterbees.ai;

import com.betterbees.mixin.PathNavigationAccessor;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

/** A path request must not leak its search budget into another activity. */
public final class NavigationBudget {
    private NavigationBudget() {}

    public static Path createPath(PathNavigation navigation, float budget, BlockPos target, int accuracy) {
        float previous = ((PathNavigationAccessor) navigation).betterbees$getMaxVisitedNodesMultiplier();
        navigation.setMaxVisitedNodesMultiplier(budget);
        try {
            return navigation.createPath(target, accuracy);
        } finally {
            navigation.setMaxVisitedNodesMultiplier(previous);
        }
    }

    public static boolean moveTo(PathNavigation navigation, float budget, double x, double y, double z, double speed) {
        float previous = ((PathNavigationAccessor) navigation).betterbees$getMaxVisitedNodesMultiplier();
        navigation.setMaxVisitedNodesMultiplier(budget);
        try {
            return navigation.moveTo(x, y, z, speed);
        } finally {
            navigation.setMaxVisitedNodesMultiplier(previous);
        }
    }
}
