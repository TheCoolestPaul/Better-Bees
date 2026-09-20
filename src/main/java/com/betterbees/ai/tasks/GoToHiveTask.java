package com.betterbees.ai.tasks;

import com.betterbees.ai.HivePathScheduler;
import com.betterbees.ai.BeeAi;
import com.betterbees.config.BetterBeesConfig;
import com.betterbees.hive.HiveSafetyService;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.util.HiveMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Map;

public final class GoToHiveTask extends Behavior<Bee> {
    private BlockPos trackedHome;
    private HivePathScheduler.Request pendingPath;
    private int consecutivePathFailures;
    private long retryAt;
    private double bestDistanceSquared = Double.MAX_VALUE;

    public GoToHiveTask() {
        super(Map.of(ModMemoryTypes.WANTS_HIVE.get(), MemoryStatus.VALUE_PRESENT,
                ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get(), MemoryStatus.VALUE_ABSENT));
    }

    private boolean valid(ServerLevel level, Bee bee) {
        BlockPos home = ((HiveMemory) bee).betterbees$getMemorizedHome();
        if (home == null || !bee.getBrain().getMemory(ModMemoryTypes.WANTS_HIVE.get()).orElse(false)
                || BeeAi.isHiveNearFire(level, bee)) return false;
        // A full home cannot receive a scheduled path. Reject it here, including
        // while queued or travelling, rather than waiting to reach entry range.
        var hive = HiveSafetyService.loadedHive(level, home);
        if (hive != null && hive.isFull()) {
            ((HiveMemory) bee).betterbees$dropAndBlacklistHive(bee);
            if (home.equals(bee.getNavigation().getTargetPos())) bee.getNavigation().stop();
            return false;
        }
        net.minecraft.world.entity.Entity leashHolder = bee.getLeashHolder();
        return leashHolder == null || home.closerToCenterThan(leashHolder.position(), 5.5D);
    }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Bee bee) { return valid(level, bee); }
    @Override protected boolean canStillUse(ServerLevel level, Bee bee, long gameTime) { return valid(level, bee); }

    @Override
    protected void start(ServerLevel level, Bee bee, long gameTime) {
        bee.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
        bee.resetLove();
        BlockPos home = ((HiveMemory) bee).betterbees$getMemorizedHome();
        if (home != null && !home.equals(trackedHome)) {
            HivePathScheduler.get(level).cancel(bee);
            pendingPath = null;
            trackedHome = home;
            consecutivePathFailures = 0;
            retryAt = gameTime;
            bestDistanceSquared = bee.distanceToSqr(home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D);
        }
    }

    @Override
    protected void tick(ServerLevel level, Bee bee, long gameTime) {
        HiveMemory memory = (HiveMemory) bee;
        BlockPos home = memory.betterbees$getMemorizedHome();
        if (home == null) return;
        if (!home.equals(trackedHome)) start(level, bee, gameTime);
        HivePathScheduler scheduler = HivePathScheduler.get(level);
        if (pendingPath != null) {
            if (pendingPath.result() == HivePathScheduler.Result.PENDING) return;
            if (pendingPath.result() == HivePathScheduler.Result.FAILED) {
                consecutivePathFailures++;
                retryAt = pendingPath.completedAt() + 20L;
                if (consecutivePathFailures >= BetterBeesConfig.hivePathFailuresBeforeBlacklist()) {
                    memory.betterbees$dropAndBlacklistHive(bee);
                    pendingPath = null;
                    return;
                }
            } else if (pendingPath.result() == HivePathScheduler.Result.REACHED) {
                consecutivePathFailures = 0;
            }
            pendingPath = null;
        }
        if (scheduler.pending(bee)) return;
        if (bee.getNavigation().isDone() && gameTime >= retryAt) {
            pendingPath = scheduler.request(bee, home, false);
            return;
        }
        double distanceSquared = bee.distanceToSqr(home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D);
        if (distanceSquared + 1.0D < bestDistanceSquared) {
            bestDistanceSquared = distanceSquared;
            consecutivePathFailures = 0;
            bee.getBrain().setMemory(ModMemoryTypes.STUCK_TICKS.get(), 0);
        }
        int travelling = bee.getBrain().getMemory(ModMemoryTypes.TRAVELLING_TICKS.get()).orElse(0) + 1;
        bee.getBrain().setMemory(ModMemoryTypes.TRAVELLING_TICKS.get(), travelling);
        if (travelling > 80 * BetterBeesConfig.maxWanderRadius()) {
            memory.betterbees$dropAndBlacklistHive(bee);
            return;
        }
        Path path = bee.getNavigation().getPath();
        Path last = bee.getBrain().getMemory(ModMemoryTypes.LAST_PATH.get()).orElse(null);
        if (path != null && last != null && (path == last || path.sameAs(last))) {
            int stuck = bee.getBrain().getMemory(ModMemoryTypes.STUCK_TICKS.get()).orElse(0) + 1;
            bee.getBrain().setMemory(ModMemoryTypes.STUCK_TICKS.get(), stuck);
            if (stuck > 600) memory.betterbees$dropAndBlacklistHive(bee);
        } else if (path != null) {
            bee.getBrain().setMemory(ModMemoryTypes.LAST_PATH.get(), path);
            bee.getBrain().setMemory(ModMemoryTypes.STUCK_TICKS.get(), 0);
        }
    }

    @Override
    protected boolean timedOut(long gameTime) {
        // Consume a completed result before honoring the behavior timeout: a long
        // queue wait must not erase a failure or skip its retry cooldown.
        return pendingPath == null && super.timedOut(gameTime);
    }

    @Override
    protected void stop(ServerLevel level, Bee bee, long gameTime) {
        HivePathScheduler.get(level).cancel(bee);
        pendingPath = null;
    }
}
