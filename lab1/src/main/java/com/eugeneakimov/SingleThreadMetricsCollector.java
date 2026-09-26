package com.eugeneakimov;

import static com.eugeneakimov.Utils.computePercentile;

public class SingleThreadMetricsCollector implements MetricsCollector {
    public static final int BUCKETS_NUM = 256;

    private final long[] buckets = new long[BUCKETS_NUM];
    private long count = 0;
    private long sum = 0;
    private long min = Long.MAX_VALUE;
    private long max = 0;

    @Override
    public void record(long value) {
        int bucket = Math.min((int) (value / 4), BUCKETS_NUM - 1);
        buckets[bucket]++;
        count++;
        sum += value;
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    @Override
    public Snapshot snapshot() {
        var bucketsCopy = buckets.clone();
        var countCopy = count;
        var sumCopy = sum;
        var minCopy = min;
        var maxCopy = max;
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
