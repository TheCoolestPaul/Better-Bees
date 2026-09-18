package com.betterbees.ai;

import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Server-thread-only FIFO. Validation does not consume a pathfinding slot. */
public final class HivePathQueue<K, V> {
    public static final int REQUESTS_PER_TICK = 8;
    private final LinkedHashMap<K, V> pending = new LinkedHashMap<>();
    private long lastTick = Long.MIN_VALUE;

    public V get(K key) { return pending.get(key); }
    public void add(K key, V value) { pending.putIfAbsent(key, value); }
    public V remove(K key) { return pending.remove(key); }
    public int size() { return pending.size(); }

    public int drain(long tick, Predicate<V> valid, Consumer<V> execute, Consumer<V> discard) {
        if (tick == lastTick) return 0;
        lastTick = tick;
        int executed = 0;
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext() && executed < REQUESTS_PER_TICK) {
            V request = iterator.next().getValue();
            iterator.remove();
            if (valid.test(request)) {
                execute.accept(request);
                executed++;
            } else {
                discard.accept(request);
            }
        }
        return executed;
    }
}
