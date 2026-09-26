package com.eugeneakimov;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Benchmark {
    public static double run(MetricsCollector collector, long[] values, int numThreads, int numSeconds) throws InterruptedException {
        var startLatch = new CountDownLatch(1);
        var stop = new AtomicBoolean(false);
        var operations = new long[numThreads];
        var threadPool = Executors.newFixedThreadPool(numThreads);

        for (int k = 0; k < numThreads; k++) {
            final int threadIndex = k;
            threadPool.submit(() -> {
                long localCount = 0;
                int valueIndex = (threadIndex * 1000) % values.length;

                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                while (!stop.get()) {
                    collector.record(values[valueIndex]);
                    localCount++;
                    valueIndex++;
                    if (valueIndex == values.length) {
                        valueIndex = 0;
                    }
                }

                operations[threadIndex] = localCount;
            });
        }

        long startedAt = System.nanoTime();
        startLatch.countDown();

        TimeUnit.SECONDS.sleep(numSeconds);

        stop.set(true);
        long stoppedAt = System.nanoTime();

        threadPool.close();

        long totalOperations = 0;
        for (long count : operations) {
            totalOperations += count;
        }

        double elapsedSeconds = (stoppedAt - startedAt) / 1_000_000_000.0;
        return totalOperations / elapsedSeconds;

    }

    public static double measurePoint(MetricsCollector collector, long[] values, int numThreads) throws InterruptedException {
        run(collector, values, numThreads, 5);

        var results = new double[5];
        for (int i = 0; i < results.length; i++) {
            results[i] = run(collector, values, numThreads, 5);
        }

        IO.println(collector.snapshot().count());

        Arrays.sort(results);
        return results[results.length / 2];
    }
}
