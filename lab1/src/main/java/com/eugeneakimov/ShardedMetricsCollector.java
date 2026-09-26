package com.eugeneakimov;

import java.util.concurrent.atomic.AtomicLong;

import static com.eugeneakimov.Utils.computePercentile;

public class ShardedMetricsCollector implements MetricsCollector {
    public static final int BUCKETS_NUM = 256;

    private final long[] buckets = new long[BUCKETS_NUM];
    private final Object[] bucketLocks = new Object[16];
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong sum = new AtomicLong();
    private final AtomicLong min = new AtomicLong(Integer.MAX_VALUE);
    private final AtomicLong max = new AtomicLong(Integer.MIN_VALUE);

    public ShardedMetricsCollector() {
        for (int i = 0; i < bucketLocks.length; i++) {
            bucketLocks[i] = new Object();
        }
    }

    @Override
    public void record(long value) {
        int bucket = Math.min((int) (value / 4), BUCKETS_NUM - 1);
        synchronized (bucketLocks[bucket % bucketLocks.length]) {
            buckets[bucket]++;
        }
        count.incrementAndGet();
        sum.addAndGet(value);
        min.updateAndGet(min -> Math.min(min, value));
        max.updateAndGet(max -> Math.max(max, value));
    }

    @Override
    public Snapshot snapshot() {
        var bucketsCopy = new long[BUCKETS_NUM];
        for (int i = 0; i < bucketLocks.length; i++) {
            synchronized (bucketLocks[i]) {
                for (int j = i; j < BUCKETS_NUM; j += bucketLocks.length) {
                    bucketsCopy[j] = buckets[j];
                }
            }
        }
        var countCopy = count.get();
        var sumCopy = sum.get();
        var minCopy = min.get();
        var maxCopy = max.get();
        var p50 = computePercentile(bucketsCopy, countCopy, 0.5);
        var p99 = computePercentile(bucketsCopy, countCopy, 0.99);
        return new Snapshot(
                bucketsCopy,
                countCopy,
                sumCopy,
                minCopy,
                maxCopy,
                p50,
                p99
        );
    }
}