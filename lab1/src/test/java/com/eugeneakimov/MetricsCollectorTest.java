package com.eugeneakimov;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsCollectorTest {
    @Test
    void shardedCollectorHasBrokenSnapshot() throws InterruptedException {
        checkBrokenSnapshot(new ShardedMetricsCollector(), false);
    }

    @Test
    void threadLocalCollectorHasBrokenSnapshot() throws InterruptedException {
        checkBrokenSnapshot(new ShardedMetricsCollector(), false);
    }

    @Test
    void doubleBufferedCollectorHasConsistentSnapshot() throws InterruptedException {
        checkBrokenSnapshot(new DoubleBufferedMetricsCollector(), true);
    }

    @Test
    void doubleBufferedCollectorDoesNotCountRecordsTwice() {
        var collector = new DoubleBufferedMetricsCollector();
        collector.record(8);
        assertEquals(1, collector.snapshot().count());
        assertEquals(1, collector.snapshot().count());
        assertEquals(1, collector.snapshot().buckets()[2]);
    }

    private static void checkBrokenSnapshot(MetricsCollector collector, boolean expectConsistent) throws InterruptedException {
        int numWriters = 4;
        var calls = new long[numWriters];
        var threads = new Thread[numWriters];
        var readyLatch = new CountDownLatch(numWriters);
        var startLatch = new CountDownLatch(1);
        var stop = new AtomicBoolean(false);

        for (int i = 0; i < numWriters; i++) {
            final int writerIndex = i;
            threads[i] = new Thread(() -> {
                long localCount = 0;
                readyLatch.countDown();
                try {
                    startLatch.await();
                    while (!stop.get()) {
                        collector.record((localCount + writerIndex) % 1024);
                        localCount++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                calls[writerIndex] = localCount;
            });
            threads[i].start();
        }

        readyLatch.await();
        startLatch.countDown();

        int less = 0;
        int greater = 0;
        for (int i = 0; i < 10_000; i++) {
            var snapshot = collector.snapshot();
            long bucketsSum = 0;
            for (long bucket : snapshot.buckets()) {
                bucketsSum += bucket;
            }
            if (bucketsSum < snapshot.count()) {
                less++;
            } else if (bucketsSum > snapshot.count()) {
                greater++;
            }
        }

        stop.set(true);
        for (var thread : threads) {
            thread.join();
        }

        long expectedCount = 0;
        for (long count : calls) {
            expectedCount += count;
        }
        long actualCount = collector.snapshot().count();
        double brokenPercent = (less + greater) / 100.0;
        IO.println(
                "%s: broken %.2f%%, sum < count: %d, sum > count: %d, count difference: %d"
                        .formatted(
                                collector.getClass().getSimpleName(),
                                brokenPercent,
                                less,
                                greater,
                                actualCount - expectedCount
                        )
        );
        assertEquals(expectedCount, actualCount);
        if (expectConsistent) {
            assertEquals(0, less + greater);
        }
    }
}
