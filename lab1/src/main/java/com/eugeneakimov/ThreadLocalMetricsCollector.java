package com.eugeneakimov;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import static com.eugeneakimov.Utils.computePercentile;

public class ThreadLocalMetricsCollector implements MetricsCollector {
    public static final int BUCKETS_NUM = 256;

    private static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(BUCKETS_NUM);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
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

    @Override
    public void record(long value) {
        ThreadState state = currentState.get();
        int bucket = (int) Math.min(value / 4, BUCKETS_NUM - 1);

        state.buckets.setRelease(bucket, state.buckets.getPlain(bucket) + 1);
        state.count.setRelease(state.count.getPlain() + 1);
        state.sum.setRelease(state.sum.getPlain() + value);

        if (value < state.min.getPlain()) state.min.setRelease(value);
        if (value > state.max.getPlain()) state.max.setRelease(value);
    }

    @Override
    public Snapshot snapshot() {
        long[] buckets = new long[BUCKETS_NUM];
        long count = 0, sum = 0, min = Long.MAX_VALUE, max = 0;

        List<ThreadState> statesCopy;
        synchronized (statesLock) {
            statesCopy = new ArrayList<>(states);
        }

        for (ThreadState state : statesCopy) {
            for (int i = 0; i < BUCKETS_NUM; i++) {
                buckets[i] += state.buckets.get(i);
            }
            count += state.count.get();
            sum += state.sum.get();
            min = Math.min(min, state.min.get());
            max = Math.max(max, state.max.get());
        }

        long p50 = computePercentile(buckets, count, 0.5);
        long p99 = computePercentile(buckets, count, 0.99);
        return new Snapshot(buckets, count, sum, min, max, p50, p99);
    }
}