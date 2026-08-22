package com.betterbees.ai.tasks;

import com.betterbees.config.BetterBeesConfig;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.util.HiveMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Map;

public final class BeePathfindingTask extends Behavior<Bee> {
    private CachedPath cached;

    public BeePathfindingTask() {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Bee bee) {
        return bee.getNavigation().isDone() && bee.getRandom().nextInt(10) == 0;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Bee bee, long gameTime) {
        return bee.getNavigation().isInProgress();
    }

    @Override
    protected void start(ServerLevel level, Bee bee, long gameTime) {
        selectOrReusePath(level, bee, gameTime);
    }

    @Override
    protected void tick(ServerLevel level, Bee bee, long gameTime) {
        if (cached != null && cached.path != null && (gameTime - cached.createdAt > 50L
                || gameTime - cached.createdAt > 5L && bee.getDeltaMovement().lengthSqr() <= 0.0025D
                || bee.blockPosition().distManhattan(cached.path.getTarget()) <= 4)) {
            selectOrReusePath(level, bee, gameTime);
        }
        if (bee.hasNectar()) bee.getBrain().setMemory(ModMemoryTypes.POLLINATING_COOLDOWN.get(), 400);
    }

    private void selectOrReusePath(ServerLevel level, Bee bee, long gameTime) {
        if (cached != null && cached.path != null && !cached.path.isDone()
                && gameTime - cached.createdAt <= 50L
                && !(bee.getDeltaMovement().lengthSqr() <= 0.0025D && gameTime - cached.createdAt > 5L)
                && bee.blockPosition().distManhattan(cached.path.getTarget()) > 4) {
            bee.getNavigation().moveTo(cached.path, 1.0D);
            return;
        }
        BlockPos origin = bee.blockPosition();
        BlockPos.MutableBlockPos candidate = origin.mutable();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, origin.getX(), origin.getZ());
        BlockPos home = ((HiveMemory) bee).betterbees$getMemorizedHome();
        Entity leashHolder = bee.getLeashHolder();
        for (int attempt = 0; attempt < 12; attempt++) {
            int y = level.dimensionType().hasCeiling() || bee.getBlockY() <= surface + 3
                    ? bee.getRandom().nextInt(6) - 2 : bee.getRandom().nextInt(6) - 5;
            candidate.setWithOffset(origin, bee.getRandom().nextInt(21) - 10, y,
                    bee.getRandom().nextInt(21) - 10);
            boolean inHome = home == null || candidate.closerThan(home, BetterBeesConfig.maxWanderRadius());
            boolean inLeash = leashHolder == null || candidate.closerToCenterThan(leashHolder.position(), 10.0D);
            if (inHome && inLeash && level.getBlockState(candidate.below(2)).isAir()) break;
        }
        Path path = bee.getNavigation().createPath(candidate.immutable(), 1);
        if (path != null) bee.getNavigation().moveTo(path, 1.0D);
        cached = new CachedPath(path, gameTime);
    }

    private static final class CachedPath {
        private final Path path;
        private final long createdAt;
        private CachedPath(Path path, long createdAt) {
            this.path = path;
            this.createdAt = createdAt;
        }
    }
}
