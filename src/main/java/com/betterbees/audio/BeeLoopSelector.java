package com.betterbees.audio;

import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/** Bounded O(n log k) selection; no Minecraft dependency so policy is testable in isolation. */
public final class BeeLoopSelector {
    private record Candidate(int id, boolean angry, double score) {}
    private static final Comparator<Candidate> BEST_FIRST = Comparator
            .comparing(Candidate::angry).reversed()
            .thenComparingDouble(Candidate::score).thenComparingInt(Candidate::id);
    private final int limit;
    private final PriorityQueue<Candidate> nearest = new PriorityQueue<>(BEST_FIRST.reversed());

    public BeeLoopSelector(int limit) { this.limit = Math.max(1, Math.min(64, limit)); }

    public void offer(int id, boolean angry, double distanceSquared, boolean alreadyPlaying) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0) return;
        // Keep incumbents unless a challenger is meaningfully closer; anger always takes priority.
        Candidate candidate = new Candidate(id, angry, distanceSquared * (alreadyPlaying ? 0.8D : 1.0D));
        if (nearest.size() < limit) nearest.add(candidate);
        else if (BEST_FIRST.compare(candidate, nearest.peek()) < 0) {
            nearest.poll();
            nearest.add(candidate);
        }
    }

    public Set<Integer> selected() {
        Set<Integer> ids = new HashSet<>();
        for (Candidate candidate : nearest) ids.add(candidate.id());
        return ids;
    }
}
