package com.betterbees.ai;

import com.betterbees.hive.HiveSafetyService;
import com.betterbees.mixin.BeeAccessor;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.util.HiveMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.schedule.Activity;

import java.lang.ref.WeakReference;
import java.util.UUID;

/** Transient state owned by a single loaded server level. */
public final class HivePathScheduler {
    public enum Result { PENDING, REACHED, FAILED, CANCELLED }

    public static final class Request {
        private final WeakReference<Bee> bee;
        private final BlockPos home;
        private final boolean recalculation;
        private Result result = Result.PENDING;
        private long completedAt;

        private Request(Bee bee, BlockPos home, boolean recalculation) {
            this.bee = new WeakReference<>(bee);
            this.home = home.immutable();
            this.recalculation = recalculation;
        }

        public Result result() { return result; }
        public long completedAt() { return completedAt; }
    }

    private final HivePathQueue<UUID, Request> queue = new HivePathQueue<>();
    private int lastTickRequests;

    public static HivePathScheduler get(ServerLevel level) {
        return ((HivePathSchedulerAccess) level).betterbees$getHivePathScheduler();
    }

    public Request request(Bee bee, BlockPos home, boolean recalculation) {
        Request existing = queue.get(bee.getUUID());
        if (existing != null && existing.bee.get() == bee && existing.home.equals(home)) return existing;
        cancel(bee);
        Request request = new Request(bee, home, recalculation);
        queue.add(bee.getUUID(), request);
        return request;
    }

    public void cancel(Bee bee) {
        Request removed = queue.remove(bee.getUUID());
        if (removed != null) removed.result = Result.CANCELLED;
    }

    public boolean pending(Bee bee) { return queue.get(bee.getUUID()) != null; }
    public int pendingCount() { return queue.size(); }
    public int lastTickRequests() { return lastTickRequests; }

    public static boolean eligible(ServerLevel level, Bee bee, BlockPos home) {
        if (home == null || bee.isRemoved() || bee.level() != level || level.getEntity(bee.getUUID()) != bee
                || !level.hasChunkAt(bee.blockPosition())
                || !home.equals(((HiveMemory) bee).betterbees$getMemorizedHome())
                || !bee.getBrain().isActive(Activity.IDLE)
                || !bee.getBrain().getMemory(ModMemoryTypes.WANTS_HIVE.get()).orElse(false)
                || bee.getBrain().hasMemoryValue(ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get())
                || bee.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                || bee.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || bee.getBrain().hasMemoryValue(MemoryModuleType.TEMPTING_PLAYER)
                || bee.getTarget() != null || bee.hasStung()
                || ((BeeAccessor) bee).betterbees$getStayOutOfHiveCountdown() > 0) return false;
        var hive = HiveSafetyService.loadedHive(level, home);
        return hive != null && !hive.isFull() && !HiveSafetyService.isFireNearby(level, hive)
                && (bee.getLeashHolder() == null || home.closerToCenterThan(bee.getLeashHolder().position(), 5.5D));
    }

    public void tick(ServerLevel level) {
        lastTickRequests = queue.drain(level.getGameTime(), request -> {
            Bee bee = request.bee.get();
            return bee != null && eligible(level, bee, request.home)
                    && (!request.recalculation || request.home.equals(bee.getNavigation().getTargetPos()));
        }, request -> {
            Bee bee = request.bee.get();
            if (request.recalculation) {
                ((HiveRecalculationAccess) bee.getNavigation()).betterbees$recomputeHivePathNow();
            } else {
                NavigationBudget.moveTo(bee.getNavigation(), 10.0F,
                        request.home.getX(), request.home.getY(), request.home.getZ(), 1.0D);
            }
            var path = bee.getNavigation().getPath();
            request.result = path != null && path.canReach() ? Result.REACHED : Result.FAILED;
            request.completedAt = level.getGameTime();
        }, request -> request.result = Result.CANCELLED);
    }
}
