package com.eugeneakimov;

import static com.eugeneakimov.Utils.generateZipf;
import static com.eugeneakimov.Utils.printBandwidth;

public class Main {
    static void singleThreadRun(long[] values) throws InterruptedException {
        var collector = new SingleThreadMetricsCollector();
        var bandwidth = Benchmark.measurePoint(collector, values, 1);
        printBandwidth(bandwidth);
    }

    static void synchronizedRun(long[] values, int numThreads) throws InterruptedException {
        var collector = new SynchronizedMetricsCollector();
        var bandwidth = Benchmark.measurePoint(collector, values, numThreads);
        printBandwidth(bandwidth);
    }

    static void emptySynchronizedRun(long[] values, int numThreads) throws InterruptedException {
        var collector = new EmptyMetricsCollector();
        var bandwidth = Benchmark.measurePoint(collector, values, numThreads);
        printBandwidth(bandwidth);
    }

    static void shardedRun(long[] values, int numThreads) throws InterruptedException {
        var collector = new ShardedMetricsCollector();
        var bandwidth = Benchmark.measurePoint(collector, values, numThreads);
        printBandwidth(bandwidth);
    }

    static void threadLocalRun(long[] values, int numThreads) throws InterruptedException {
        var collector = new ThreadLocalMetricsCollector();
        var bandwidth = Benchmark.measurePoint(collector, values, numThreads);
        printBandwidth(bandwidth);
    }

    static void doubleBufferedRun(long[] values, int numThreads) throws InterruptedException {
        var collector = new DoubleBufferedMetricsCollector();
        var bandwidth = Benchmark.measurePoint(collector, values, numThreads);
        printBandwidth(bandwidth);
    }

    static void main() throws InterruptedException {
        var values = generateZipf(1 << 20, 1023, 0);
        doubleBufferedRun(values, 14);
    }
}
