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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A bounded, transient and incrementally populated flower index owned by one loaded hive. */
public final class HiveFlowerIndex {
    public enum Status { FOUND, SCANNING, COMPLETE_MISS }
    public record Request(Status status, BlockPos flower, long completedGeneration) {}

    private static final long ACTIVE_TICKS = 1_200L;
    private static final double RESERVATION_PENALTY = 16.0D;

    private final List<BlockPos> flowers = new ArrayList<>();
    private final Set<BlockPos> flowerMembership = new HashSet<>();
    private final Map<UUID, Reservation> reservations = new HashMap<>();
    private final Map<BlockPos, Integer> reservationCounts = new HashMap<>();
    private final Set<UUID> waitingBees = new HashSet<>();
    private final BlockPos.MutableBlockPos scanPosition = new BlockPos.MutableBlockPos();
    private long activeUntil = Long.MIN_VALUE;
    private long completedGeneration;
    private long nextReservationExpiry = Long.MAX_VALUE;
    private int shell;
    private int shellCursor;
    private int permutationShell = -1;
    private int permutationCount = 1;
    private int permutationStart;
    private int permutationStep = 1;
    private int lastTickChecks;
    private int lastTickCacheProbes;
    private int lastTickGenerationCompletions;

    public Request request(ServerLevel level, BlockPos hivePos, Bee bee, long handledGeneration) {
        long now = level.getGameTime();
        activeUntil = Math.max(activeUntil, now + ACTIVE_TICKS);
        cleanReservations(now);

        UUID beeId = bee.getUUID();
        removeReservation(beeId);
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
                flowerMembership.remove(flower);
                removeReservationsAt(flower);
                continue;
            }

            int reservationCount = reservationCounts.getOrDefault(flower, 0);
            double distance = bee.distanceToSqr(flower.getX() + 0.5D, flower.getY() + 0.5D, flower.getZ() + 0.5D);
            double score = distance + reservationCount * RESERVATION_PENALTY;
            if (score < bestScore || (score == bestScore && comparePositions(flower, best) < 0)) {
                bestScore = score;
                best = flower;
            }
        }

        if (best != null) {
            waitingBees.remove(beeId);
            addReservation(beeId, best, now + ACTIVE_TICKS);
            return new Request(Status.FOUND, best, completedGeneration);
        }
        if (completedGeneration > handledGeneration) {
            waitingBees.remove(beeId);
            return new Request(Status.COMPLETE_MISS, null, completedGeneration);
        }
        waitingBees.add(beeId);
        return new Request(Status.SCANNING, null, completedGeneration);
    }

    public void release(UUID beeId) {
        waitingBees.remove(beeId);
        removeReservation(beeId);
    }

    public void invalidate(BlockPos flower) {
        if (!flowerMembership.remove(flower)) return;
        flowers.remove(flower);
        removeReservationsAt(flower);
    }

    public void tick(ServerLevel level, BlockPos hivePos) {
        lastTickChecks = 0;
        lastTickCacheProbes = 0;
        lastTickGenerationCompletions = 0;
        long now = level.getGameTime();
        cleanReservations(now);
        if (now > activeUntil) {
            waitingBees.clear();
            return;
        }
        if (waitingBees.isEmpty()) return;

        int radius = BetterBeesConfig.maxWanderRadius();
        long radiusSquared = (long) radius * radius;
        int budget = BetterBeesConfig.flowerScanBudget();
        while (lastTickChecks < budget) {
            if (shell > radius) {
                completedGeneration = Math.max(completedGeneration + 1L, now + 1L);
                lastTickGenerationCompletions++;
                waitingBees.clear();
                resetScanner();
                return;
            }
            int count = shellCount(shell);
            if (shellCursor >= count) {
                shell++;
                shellCursor = 0;
                permutationShell = -1;
                continue;
            }

            preparePermutation(count, hivePos);
            int permuted = (int) ((permutationStart + (long) shellCursor++ * permutationStep) % count);
            int distanceSquared = decodeShell(shell, permuted, hivePos, scanPosition);
            if (distanceSquared > radiusSquared) continue;
            lastTickChecks++;
            if (!level.hasChunkAt(scanPosition)) continue;
            if (isFlower(level, scanPosition)) {
                remember(scanPosition, hivePos);
            } else {
                lastTickCacheProbes++;
                if (flowerMembership.contains(scanPosition)) invalidate(scanPosition);
            }
        }
    }

    public int cachedFlowerCount() { return flowers.size(); }
    public int lastTickChecks() { return lastTickChecks; }
    public int lastTickCacheProbes() { return lastTickCacheProbes; }
    public int activeDemandCount() { return waitingBees.size(); }
    public int reservationCount(BlockPos flower) { return reservationCounts.getOrDefault(flower, 0); }
    public int lastTickGenerationCompletions() { return lastTickGenerationCompletions; }
    public long completedGeneration() { return completedGeneration; }

    private void remember(BlockPos position, BlockPos hivePos) {
        if (flowerMembership.contains(position)) return;
        int capacity = BetterBeesConfig.flowerCacheSize();
        BlockPos immutable = position.immutable();
        if (flowers.size() < capacity) {
            flowers.add(immutable);
            flowerMembership.add(immutable);
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
            BlockPos removed = flowers.set(worstIndex, immutable);
            flowerMembership.remove(removed);
            flowerMembership.add(immutable);
            removeReservationsAt(removed);
        }
    }

    private static boolean isFlower(ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        return state.is(BlockTags.FLOWERS) && state.getFluidState().isEmpty();
    }

    private void addReservation(UUID beeId, BlockPos position, long expiresAt) {
        reservations.put(beeId, new Reservation(position, expiresAt));
        reservationCounts.merge(position, 1, Integer::sum);
        nextReservationExpiry = Math.min(nextReservationExpiry, expiresAt);
    }

    private void removeReservation(UUID beeId) {
        Reservation removed = reservations.remove(beeId);
        if (removed != null) decrementReservationCount(removed.position);
    }

    private void decrementReservationCount(BlockPos position) {
        reservationCounts.computeIfPresent(position, (ignored, count) -> count <= 1 ? null : count - 1);
    }

    private void cleanReservations(long now) {
        if (now < nextReservationExpiry) return;
        nextReservationExpiry = Long.MAX_VALUE;
        for (Iterator<Map.Entry<UUID, Reservation>> iterator = reservations.entrySet().iterator(); iterator.hasNext();) {
            Reservation reservation = iterator.next().getValue();
            if (reservation.expiresAt <= now) {
                iterator.remove();
                decrementReservationCount(reservation.position);
            } else {
                nextReservationExpiry = Math.min(nextReservationExpiry, reservation.expiresAt);
            }
        }
    }

    private void removeReservationsAt(BlockPos position) {
        for (Iterator<Map.Entry<UUID, Reservation>> iterator = reservations.entrySet().iterator(); iterator.hasNext();) {
            if (iterator.next().getValue().position.equals(position)) iterator.remove();
        }
        reservationCounts.remove(position);
        recomputeNextReservationExpiry();
    }

    private void recomputeNextReservationExpiry() {
        nextReservationExpiry = Long.MAX_VALUE;
        for (Reservation reservation : reservations.values()) {
            nextReservationExpiry = Math.min(nextReservationExpiry, reservation.expiresAt);
        }
    }

    private void resetScanner() {
        shell = 0;
        shellCursor = 0;
        permutationShell = -1;
    }

    private void preparePermutation(int count, BlockPos hivePos) {
        if (permutationShell == shell && permutationCount == count) return;
        long seed = hivePos.asLong() ^ ((long) shell << 32);
        permutationShell = shell;
        permutationCount = count;
        permutationStart = count <= 1 ? 0 : Math.floorMod((int) mix(seed), count);
        permutationStep = count <= 1 ? 1
                : Math.floorMod((int) (mix(seed + 0x9E3779B97F4A7C15L) | 1L), count);
        if (permutationStep == 0) permutationStep = 1;
        while (gcd(permutationStep, count) != 1) {
            int next = (permutationStep + 2) % count;
            permutationStep = next == 0 ? 1 : next;
        }
    }

    private static int shellCount(int radius) {
        if (radius == 0) return 1;
        return 24 * radius * radius + 2;
    }

    private static int decodeShell(int radius, int index, BlockPos origin, BlockPos.MutableBlockPos result) {
        int x;
        int y;
        int z;
        if (radius == 0) {
            x = y = z = 0;
        } else {
            int width = radius * 2 + 1;
            int cap = width * width;
            if (index < cap * 2) {
                int face = index / cap;
                int local = index % cap;
                x = local % width - radius;
                y = face == 0 ? -radius : radius;
                z = local / width - radius;
            } else {
                index -= cap * 2;
                int inner = radius * 2 - 1;
                int side = width * inner;
                if (index < side * 2) {
                    int face = index / side;
                    int local = index % side;
                    x = local % width - radius;
                    y = local / width - radius + 1;
                    z = face == 0 ? -radius : radius;
                } else {
                    index -= side * 2;
                    int end = inner * inner;
                    int face = index / end;
                    int local = index % end;
                    x = face == 0 ? -radius : radius;
                    y = local % inner - radius + 1;
                    z = local / inner - radius + 1;
                }
            }
        }
        result.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
        return x * x + y * y + z * z;
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
