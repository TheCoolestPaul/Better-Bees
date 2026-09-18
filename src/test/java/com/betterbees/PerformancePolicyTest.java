package com.betterbees;

import com.betterbees.audio.BeeLoopSelector;
import com.betterbees.ai.HivePathQueue;
import com.betterbees.hive.HiveRuntimeState;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Dependency-free regression tests, also runnable before Minecraft artifacts are available. */
public final class PerformancePolicyTest {
    public static void main(String[] args) {
        hivePathScheduling();
        sharedFireChecks();
        transitionBursts();
        nearestAndAngerPriority();
        stableSelectionAndRecovery();
        System.out.println("All 5 performance policy scenarios passed");
    }

    private static void hivePathScheduling() {
        HivePathQueue<Integer, Integer> queue = new HivePathQueue<>();
        var executed = new java.util.ArrayList<Integer>();
        for (int bee = 0; bee < 200; bee++) queue.add(bee, bee);
        queue.add(0, 999); // Repeated block updates must not duplicate or replace waiting work.
        for (long tick = 0; tick < 25; tick++) {
            require(queue.drain(tick, value -> true, executed::add, value -> {}) == 8, "eight paths per tick");
            require(queue.drain(tick, value -> true, executed::add, value -> {}) == 0, "same tick cannot double budget");
        }
        require(queue.size() == 0 && executed.size() == 200, "200 requests drain in 25 ticks");
        for (int bee = 0; bee < 200; bee++) require(executed.get(bee) == bee, "FIFO prevents starvation");
        queue.add(200, 200);
        queue.add(201, 201);
        require(queue.remove(200) == 200, "cancel pending request");
        queue.add(200, 200); // A changed destination joins the back.
        executed.clear();
        AtomicInteger discarded = new AtomicInteger();
        for (int bee = 202; bee < 210; bee++) queue.add(bee, bee);
        require(queue.drain(25, value -> value != 201, executed::add, value -> discarded.incrementAndGet()) == 8,
                "stale request does not consume budget");
        require(discarded.get() == 1 && executed.get(0) == 200 && queue.size() == 1, "cancellation and FIFO");
        HivePathQueue<Integer, Integer> otherDimension = new HivePathQueue<>();
        otherDimension.add(1, 1);
        require(otherDimension.drain(25, value -> true, value -> {}, value -> {}) == 1, "dimension budgets independent");
        require(queue.drain(0, value -> true, value -> {}, value -> {}) == 1, "clock reset cannot stall queue");
    }

    private static void sharedFireChecks() {
        HiveRuntimeState hive = new HiveRuntimeState();
        AtomicInteger scans = new AtomicInteger();
        for (int bee = 0; bee < 120; bee++) {
            require(!hive.fireNearby(100, () -> { scans.incrementAndGet(); return false; }), "cached safe state");
        }
        require(scans.get() == 1, "120 bees should cause only one shared scan");
        require(hive.fireNearby(101, () -> { scans.incrementAndGet(); return true; }), "fire becomes visible next tick");
        require(scans.get() == 2, "new tick must scan again");
        require(!new HiveRuntimeState().fireNearby(101, () -> false), "replacement hive must not inherit cache");
    }

    private static void transitionBursts() {
        for (int bees : new int[]{20, 60, 120}) {
            HiveRuntimeState hive = new HiveRuntimeState();
            int sounds = 0;
            for (int bee = 0; bee < bees; bee++) if (hive.allowTransitionSound(100, 5)) sounds++;
            require(sounds == 1, "one immediate sound per burst");
            require(!hive.allowTransitionSound(104, 5), "combined entry/exit cooldown");
            require(hive.allowTransitionSound(105, 5), "inclusive interval boundary");
            require(new HiveRuntimeState().allowTransitionSound(100, 5), "separate hive has separate budget");
            for (int bee = 0; bee < bees; bee++) require(hive.allowTransitionSound(105, 0), "opt out preserves every sound");
            require(hive.allowTransitionSound(1, 5), "clock reset must not silence hive indefinitely");
        }
    }

    private static void nearestAndAngerPriority() {
        BeeLoopSelector selector = new BeeLoopSelector(8);
        for (int id = 120; id >= 1; id--) selector.offer(id, false, id, false);
        selector.offer(999, true, 250, false);
        require(selector.selected().equals(Set.of(1, 2, 3, 4, 5, 6, 7, 999)), "nearest loops with angry priority and hard cap");
    }

    private static void stableSelectionAndRecovery() {
        BeeLoopSelector stable = new BeeLoopSelector(1);
        stable.offer(1, false, 100, true);
        stable.offer(2, false, 90, false);
        require(stable.selected().equals(Set.of(1)), "small movement must not churn loops");
        BeeLoopSelector moved = new BeeLoopSelector(1);
        moved.offer(1, false, 100, true);
        moved.offer(2, false, 60, false);
        require(moved.selected().equals(Set.of(2)), "suppressed bee becomes selected as listener moves");
        BeeLoopSelector removed = new BeeLoopSelector(1);
        removed.offer(1, false, Double.NaN, false);
        removed.offer(2, false, 60, false);
        require(removed.selected().equals(Set.of(2)), "removal and invalid distance cannot consume a slot");
        BeeLoopSelector anger = new BeeLoopSelector(1);
        anger.offer(1, false, 1, true);
        anger.offer(2, true, 200, false);
        require(anger.selected().equals(Set.of(2)), "angry newcomer preempts calm incumbent");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
