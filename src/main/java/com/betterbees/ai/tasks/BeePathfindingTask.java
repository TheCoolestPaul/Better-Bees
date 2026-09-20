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
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class BeePathfindingTask extends Behavior<Bee> {
    private Path ownedPath;
    private Vec3 progressPosition;
    private long progressTime;
    private int progressNode;

    public BeePathfindingTask() {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Bee bee) {
        return !returningHome(bee) && bee.getNavigation().isDone() && bee.getRandom().nextInt(10) == 0;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Bee bee, long gameTime) {
        return !returningHome(bee) && bee.getNavigation().isInProgress();
    }

    private static boolean returningHome(Bee bee) {
        BlockPos home = ((HiveMemory) bee).betterbees$getMemorizedHome();
        Entity leashHolder = bee.getLeashHolder();
        return home != null && bee.getBrain().getMemory(ModMemoryTypes.WANTS_HIVE.get()).orElse(false)
                && !bee.getBrain().hasMemoryValue(ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get())
                && (leashHolder == null || home.closerToCenterThan(leashHolder.position(), 5.5D));
    }

    @Override
    protected void start(ServerLevel level, Bee bee, long gameTime) {
        selectPath(level, bee, gameTime);
    }

    @Override
    protected void tick(ServerLevel level, Bee bee, long gameTime) {
        // Never replace a route installed by another behavior (flower, temptation, or combat).
        if (ownedPath != null && bee.getNavigation().getPath() == ownedPath) {
            if (ownedPath.getNextNodeIndex() != progressNode
                    || bee.position().distanceToSqr(progressPosition) >= 0.25D) {
                rememberProgress(bee, gameTime);
            } else if (gameTime - progressTime >= 40L) {
                bee.getNavigation().stop();
                selectPath(level, bee, gameTime);
            }
        }
        if (bee.hasNectar()) bee.getBrain().setMemory(ModMemoryTypes.POLLINATING_COOLDOWN.get(), 400);
    }

    private void rememberProgress(Bee bee, long gameTime) {
        progressPosition = bee.position();
        progressTime = gameTime;
        progressNode = ownedPath.getNextNodeIndex();
    }

    private void selectPath(ServerLevel level, Bee bee, long gameTime) {
        ownedPath = null;
        BlockPos origin = bee.blockPosition();
        BlockPos.MutableBlockPos candidate = origin.mutable();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, origin.getX(), origin.getZ());
        BlockPos home = ((HiveMemory) bee).betterbees$getMemorizedHome();
        Entity leashHolder = bee.getLeashHolder();
        boolean found = false;
        for (int attempt = 0; attempt < 12; attempt++) {
            int y = level.dimensionType().hasCeiling() || bee.getBlockY() <= surface + 3
                    ? bee.getRandom().nextInt(6) - 2 : bee.getRandom().nextInt(6) - 5;
            candidate.setWithOffset(origin, bee.getRandom().nextInt(21) - 10, y,
                    bee.getRandom().nextInt(21) - 10);
            boolean inHome = home == null || candidate.closerThan(home, BetterBeesConfig.maxWanderRadius());
            boolean inLeash = leashHolder == null || candidate.closerToCenterThan(leashHolder.position(), 10.0D);
            if (inHome && inLeash && level.hasChunkAt(candidate)
                    && origin.distManhattan(candidate) > 1 && level.getBlockState(candidate.below(2)).isAir()) {
                found = true;
                break;
            }
        }
        if (!found) return;
        Path path = bee.getNavigation().createPath(candidate.immutable(), 1);
        if (path != null && bee.getNavigation().moveTo(path, 1.0D)) {
            ownedPath = path;
            rememberProgress(bee, gameTime);
        }
    }
}
