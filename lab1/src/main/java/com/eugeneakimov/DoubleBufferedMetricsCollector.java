package com.eugeneakimov;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.eugeneakimov.Utils.computePercentile;

public class DoubleBufferedMetricsCollector implements MetricsCollector {
    public static final int BUCKETS_NUM = 256;
    private static final int NOWHERE = -1;

    private static final class ThreadState {
        final long[][] buckets = new long[2][256];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {0, 0};

        // Флаг входа: -1 = вне буферов, 0 = запись в буфер 0, 1 = запись в буфер 1
        final AtomicInteger inside = new AtomicInteger(NOWHERE);
    }

    private final List<ThreadState> states = new ArrayList<>();
    private final Object statesLock = new Object();
    private final ThreadLocal<ThreadState> currentState = ThreadLocal.withInitial(() -> {
        ThreadState state = new ThreadState();
        synchronized (statesLock) {
            states.add(state);
        }
        return state;
    });


    private volatile int active = 0;

    private final long[] buckets = new long[BUCKETS_NUM];
    private long count = 0;
    private long sum = 0;
    private long min = Long.MAX_VALUE;
    private long max = 0;

    @Override
    public void record(long value) {
        ThreadState state = currentState.get();
        int current;
        while (true) {
            current = active;
            state.inside.set(current);
            if (current == active) break;
            state.inside.setRelease(NOWHERE);
        }

        int bucket = (int) Math.min(value / 4, BUCKETS_NUM - 1);
        state.buckets[current][bucket]++;
        state.count[current]++;
        state.sum[current] += value;
        state.min[current] = Math.min(state.min[current], value);
        state.max[current] = Math.max(state.max[current], value);
        state.inside.setRelease(NOWHERE);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (statesLock) {
            int oldActive = active;
            active = 1 - oldActive;
            for (var state : states) {
                while (state.inside.get() == oldActive) {
                    Thread.onSpinWait();
                }
                for (int i = 0; i < BUCKETS_NUM; i++) {
                    buckets[i] += state.buckets[oldActive][i];
                }
                count += state.count[oldActive];
                sum += state.sum[oldActive];
                min = Math.min(min, state.min[oldActive]);
                max = Math.max(max, state.max[oldActive]);

                Arrays.fill(state.buckets[oldActive], 0);
                state.count[oldActive] = 0;
                state.sum[oldActive] = 0;
                state.min[oldActive] = Long.MAX_VALUE;
                state.max[oldActive] = 0;
            }
            long p50 = computePercentile(buckets, count, 0.5);
            long p99 = computePercentile(buckets, count, 0.99);
            return new Snapshot(buckets.clone(), count, sum, min, max, p50, p99);
        }
    }
}
