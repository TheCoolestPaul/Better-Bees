package com.betterbees.hive;

import com.betterbees.config.BetterBeesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A bounded, transient and incrementally populated flower index owned by one loaded hive. */
public final class HiveFlowerIndex {
    public enum Status { FOUND, SCANNING, COMPLETE_MISS }
    public record Request(Status status, BlockPos flower, long completedGeneration) {}

    private static final long ACTIVE_TICKS = 1_200L;
    private static final double RESERVATION_PENALTY = 16.0D;

    private final List<BlockPos> flowers = new ArrayList<>();
    private final Map<UUID, Reservation> reservations = new HashMap<>();
    private long activeUntil = Long.MIN_VALUE;
    private long completedGeneration;
    private int shell;
    private int shellCursor;
    private int lastTickChecks;

    public Request request(ServerLevel level, BlockPos hivePos, Bee bee) {
        long now = level.getGameTime();
        activeUntil = Math.max(activeUntil, now + ACTIVE_TICKS);
        cleanReservations(now);

        UUID beeId = bee.getUUID();
        reservations.remove(beeId);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int locateRange = BetterBeesConfig.flowerLocateRange();
        int rangeSquared = locateRange * locateRange;
        Entity leashHolder = bee.getLeashHolder();

        for (Iterator<BlockPos> iterator = flowers.iterator(); iterator.hasNext();) {
            BlockPos flower = iterator.next();
            if (!level.hasChunkAt(flower)) continue;
            if (bee.blockPosition().distSqr(flower) > rangeSquared) continue;
            if (leashHolder != null && !flower.closerToCenterThan(leashHolder.position(), 5.5D)) continue;
            if (!isFlower(level, flower)) {
                iterator.remove();
                removeReservationsAt(flower);
                continue;
            }

            int reservationCount = 0;
            for (Reservation reservation : reservations.values()) {
                if (reservation.position.equals(flower)) reservationCount++;
            }
            double distance = bee.distanceToSqr(flower.getX() + 0.5D, flower.getY() + 0.5D, flower.getZ() + 0.5D);
            double score = distance + reservationCount * RESERVATION_PENALTY;
            if (score < bestScore || (score == bestScore && comparePositions(flower, best) < 0)) {
                bestScore = score;
                best = flower;
            }
        }

        if (best != null) {
            reservations.put(beeId, new Reservation(best, now + ACTIVE_TICKS));
            return new Request(Status.FOUND, best, completedGeneration);
        }
        return new Request(completedGeneration == 0 ? Status.SCANNING : Status.COMPLETE_MISS,
                null, completedGeneration);
    }

    public void release(UUID beeId) {
        reservations.remove(beeId);
    }

    public void invalidate(BlockPos flower) {
        flowers.remove(flower);
        removeReservationsAt(flower);
    }

    public void tick(ServerLevel level, BlockPos hivePos) {
        lastTickChecks = 0;
        long now = level.getGameTime();
        cleanReservations(now);
        if (now > activeUntil) return;

        int radius = BetterBeesConfig.maxWanderRadius();
        int budget = BetterBeesConfig.flowerScanBudget();
        while (lastTickChecks < budget) {
            if (shell > radius) {
                completedGeneration++;
                shell = 0;
                shellCursor = 0;
            }
            int count = shellCount(shell);
            if (shellCursor >= count) {
                shell++;
                shellCursor = 0;
                continue;
            }

            int permuted = permute(shellCursor++, count, hivePos.asLong() ^ ((long) shell << 32));
            BlockPos offset = decodeShell(shell, permuted);
            if (offset.distSqr(BlockPos.ZERO) > (long) radius * radius) continue;
            lastTickChecks++;
            BlockPos candidate = hivePos.offset(offset);
            if (!level.hasChunkAt(candidate)) continue;
            if (isFlower(level, candidate)) remember(candidate, hivePos);
            else invalidate(candidate);
        }
    }

    public int cachedFlowerCount() { return flowers.size(); }
    public int lastTickChecks() { return lastTickChecks; }
    public long completedGeneration() { return completedGeneration; }

    private void remember(BlockPos position, BlockPos hivePos) {
        if (flowers.contains(position)) return;
        int capacity = BetterBeesConfig.flowerCacheSize();
        if (flowers.size() < capacity) {
            flowers.add(position.immutable());
            return;
        }
        long candidatePriority = priority(position, hivePos);
        int worstIndex = 0;
        long worstPriority = priority(flowers.get(0), hivePos);
        for (int i = 1; i < flowers.size(); i++) {
            long priority = priority(flowers.get(i), hivePos);
            if (Long.compareUnsigned(priority, worstPriority) > 0) {
                worstPriority = priority;
                worstIndex = i;
            }
        }
        if (Long.compareUnsigned(candidatePriority, worstPriority) < 0) {
            BlockPos removed = flowers.set(worstIndex, position.immutable());
            removeReservationsAt(removed);
        }
    }

    private static boolean isFlower(ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        return state.is(BlockTags.FLOWERS) && state.getFluidState().isEmpty();
    }

    private void cleanReservations(long now) {
        reservations.values().removeIf(reservation -> reservation.expiresAt <= now);
    }

    private void removeReservationsAt(BlockPos position) {
        reservations.values().removeIf(reservation -> reservation.position.equals(position));
    }

    private static int shellCount(int radius) {
        if (radius == 0) return 1;
        return 24 * radius * radius + 2;
    }

    private static BlockPos decodeShell(int radius, int index) {
        if (radius == 0) return BlockPos.ZERO;
        int width = radius * 2 + 1;
        int cap = width * width;
        if (index < cap * 2) {
            int face = index / cap;
            int local = index % cap;
            return new BlockPos(local % width - radius, face == 0 ? -radius : radius, local / width - radius);
        }
        index -= cap * 2;
        int inner = radius * 2 - 1;
        int side = width * inner;
        if (index < side * 2) {
            int face = index / side;
            int local = index % side;
            return new BlockPos(local % width - radius, local / width - radius + 1, face == 0 ? -radius : radius);
        }
        index -= side * 2;
        int end = inner * inner;
        int face = index / end;
        int local = index % end;
        return new BlockPos(face == 0 ? -radius : radius, local % inner - radius + 1, local / inner - radius + 1);
    }

    private static int permute(int index, int count, long seed) {
        if (count <= 1) return 0;
        int start = Math.floorMod((int) mix(seed), count);
        int step = Math.floorMod((int) (mix(seed + 0x9E3779B97F4A7C15L) | 1L), count);
        if (step == 0) step = 1;
        while (gcd(step, count) != 1) step = (step + 2) % count == 0 ? 1 : (step + 2) % count;
        return (int) ((start + (long) index * step) % count);
    }

    private static int gcd(int a, int b) {
        while (b != 0) { int next = a % b; a = b; b = next; }
        return Math.abs(a);
    }

    private static long priority(BlockPos position, BlockPos hivePos) {
        return mix(position.asLong() ^ Long.rotateLeft(hivePos.asLong(), 21));
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }

    private static int comparePositions(BlockPos left, BlockPos right) {
        return right == null ? -1 : Long.compare(left.asLong(), right.asLong());
    }

    private record Reservation(BlockPos position, long expiresAt) {}
}
