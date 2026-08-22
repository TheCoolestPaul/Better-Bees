package com.betterbees.ai.tasks;

import com.betterbees.config.BetterBeesConfig;
import com.betterbees.hive.HiveFlowerIndex;
import com.betterbees.hive.HiveFlowerService;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.util.HiveMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.AirRandomPos;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class FindFlowerTask extends Behavior<Bee> {
    private BlockPos candidate;
    private BlockPos sharedHome;
    private long handledGeneration;
    private LocalSearch localSearch;
    private int pathFailures;

    public FindFlowerTask() {
        super(Map.of(ModMemoryTypes.POLLINATING_COOLDOWN.get(), MemoryStatus.VALUE_ABSENT), 600);
    }

    private boolean searching(Bee bee) {
        return !bee.hasNectar() && bee.getBrain().getMemory(ModMemoryTypes.FLOWER_POS.get()).isEmpty()
                && !bee.getBrain().getMemory(ModMemoryTypes.WANTS_HIVE.get()).orElse(false);
    }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Bee bee) { return searching(bee); }
    @Override protected boolean canStillUse(ServerLevel level, Bee bee, long gameTime) {
        return searching(bee) && (candidate != null || localSearch != null);
    }

    @Override
    protected void start(ServerLevel level, Bee bee, long gameTime) {
        candidate = null;
        pathFailures = 0;
        BlockPos home = usableSharedHome(level, bee);
        if (home != null) {
            localSearch = null;
            if (!home.equals(sharedHome)) {
                if (sharedHome != null) HiveFlowerService.release(level, sharedHome, bee);
                sharedHome = home;
                handledGeneration = 0;
            }
            requestShared(level, bee, home);
            return;
        }

        if (sharedHome != null) HiveFlowerService.release(level, sharedHome, bee);
        sharedHome = null;
        Entity leashHolder = bee.getLeashHolder();
        BlockPos origin = leashHolder == null ? bee.blockPosition() : leashHolder.blockPosition();
        int radius = leashHolder == null ? BetterBeesConfig.flowerLocateRange() : 5;
        if (localSearch == null || localSearch.radius != radius
                || (leashHolder != null && !localSearch.origin.equals(origin))) {
            localSearch = new LocalSearch(origin, radius);
        }
        scanLocal(level, bee);
    }

    @Override
    protected void tick(ServerLevel level, Bee bee, long gameTime) {
        if (candidate == null) {
            if (localSearch != null) scanLocal(level, bee);
            return;
        }
        if (!validFlower(level, candidate)) {
            abandonAndFail(level, bee, true);
            return;
        }
        Entity leashHolder = bee.getLeashHolder();
        if (leashHolder != null && !candidate.closerToCenterThan(leashHolder.position(), 5.5D)) {
            abandonAndFail(level, bee, false);
            return;
        }
        if (bee.getNavigation().isDone() && !pathRandomlyTowards(bee, candidate)) {
            if (++pathFailures >= 3) abandonAndFail(level, bee, false);
            return;
        }
        if (!bee.getNavigation().isDone()) pathFailures = 0;
        if (bee.blockPosition().closerThan(candidate, 2.0D) && validFlower(level, candidate)) {
            bee.getBrain().setMemory(ModMemoryTypes.FLOWER_POS.get(), GlobalPos.of(level.dimension(), candidate));
            bee.getBrain().setMemory(ModMemoryTypes.SEARCH_ATTEMPTS.get(), 0);
            candidate = null;
            localSearch = null;
        }
    }

    private void requestShared(ServerLevel level, Bee bee, BlockPos home) {
        HiveFlowerIndex.Request request = HiveFlowerService.request(level, home, bee);
        if (request.status() == HiveFlowerIndex.Status.FOUND) {
            candidate = request.flower();
        } else if (request.status() == HiveFlowerIndex.Status.COMPLETE_MISS
                && request.completedGeneration() > handledGeneration) {
            handledGeneration = request.completedGeneration();
            fail(level, bee);
        } else {
            retrySoon(level, bee);
        }
    }

    private void scanLocal(ServerLevel level, Bee bee) {
        if (localSearch == null) return;
        int budget = BetterBeesConfig.flowerScanBudget();
        for (int checked = 0; checked < budget && !localSearch.complete(); checked++) {
            BlockPos position = localSearch.next();
            if (!level.hasChunkAt(position) || !validFlower(level, position)) continue;
            Entity leashHolder = bee.getLeashHolder();
            if (leashHolder == null || position.closerToCenterThan(leashHolder.position(), 5.5D)) {
                candidate = position;
                localSearch = null;
                return;
            }
        }
        if (localSearch != null && localSearch.complete()) {
            localSearch = null;
            fail(level, bee);
        }
    }

    private void abandonAndFail(ServerLevel level, Bee bee, boolean invalidate) {
        if (sharedHome != null && candidate != null) {
            if (invalidate) HiveFlowerService.invalidate(level, sharedHome, candidate);
            HiveFlowerService.release(level, sharedHome, bee);
        }
        candidate = null;
        localSearch = null;
        fail(level, bee);
    }

    private static BlockPos usableSharedHome(ServerLevel level, Bee bee) {
        BlockPos home = ((HiveMemory) bee).betterbees$getMemorizedHome();
        if (home == null || !(level.getBlockEntity(home) instanceof BeehiveBlockEntity)) return null;
        Entity leashHolder = bee.getLeashHolder();
        return leashHolder == null || home.closerToCenterThan(leashHolder.position(), 5.5D) ? home : null;
    }

    private static boolean validFlower(ServerLevel level, BlockPos position) {
        return level.hasChunkAt(position) && level.getBlockState(position).is(BlockTags.FLOWERS)
                && level.getBlockState(position).getFluidState().isEmpty();
    }

    private static boolean pathRandomlyTowards(Bee bee, BlockPos target) {
        Vec3 targetVec = Vec3.atBottomCenterOf(target);
        int yDelta = target.getY() - bee.getBlockY();
        int yAdjust = yDelta > 2 ? 4 : yDelta < -2 ? -4 : 0;
        int distance = bee.blockPosition().distManhattan(target);
        int horizontal = distance < 15 ? Math.max(1, distance / 2) : 6;
        int vertical = distance < 15 ? Math.max(1, distance / 2) : 8;
        Vec3 next = AirRandomPos.getPosTowards(bee, horizontal, vertical, yAdjust, targetVec, (float) Math.PI / 10.0F);
        if (next != null) {
            bee.getNavigation().setMaxVisitedNodesMultiplier(0.5F);
            bee.getNavigation().moveTo(next.x, next.y, next.z, 0.6D);
        }
        Path path = bee.getNavigation().getPath();
        return path != null && path.canReach();
    }

    private static void retrySoon(ServerLevel level, Bee bee) {
        bee.getBrain().setMemory(ModMemoryTypes.POLLINATING_COOLDOWN.get(), UniformInt.of(20, 40).sample(level.random));
    }

    private static void fail(ServerLevel level, Bee bee) {
        int attempts = bee.getBrain().getMemory(ModMemoryTypes.SEARCH_ATTEMPTS.get()).orElse(0) + 1;
        bee.getBrain().setMemory(ModMemoryTypes.SEARCH_ATTEMPTS.get(), attempts);
        bee.getBrain().setMemory(ModMemoryTypes.POLLINATING_COOLDOWN.get(), UniformInt.of(120, 240).sample(level.random));
        if (attempts >= BetterBeesConfig.searchAttempts()) {
            bee.getBrain().setMemory(ModMemoryTypes.WANTS_HIVE.get(), true);
        }
    }

    private static final class LocalSearch {
        private final BlockPos origin;
        private final int radius;
        private final int width;
        private final int count;
        private int cursor;

        private LocalSearch(BlockPos origin, int radius) {
            this.origin = origin.immutable();
            this.radius = radius;
            this.width = radius * 2 + 1;
            this.count = width * width * width;
        }

        private BlockPos next() {
            int index = cursor++;
            int x = index % width - radius;
            int y = index / width % width - radius;
            int z = index / (width * width) - radius;
            return origin.offset(x, y, z);
        }

        private boolean complete() { return cursor >= count; }
    }
}
