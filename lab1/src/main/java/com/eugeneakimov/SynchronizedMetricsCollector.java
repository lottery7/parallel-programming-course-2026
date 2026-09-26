package com.eugeneakimov;

public class SynchronizedMetricsCollector implements MetricsCollector {
    private final SingleThreadMetricsCollector collector = new SingleThreadMetricsCollector();

    @Override
    public synchronized void record(long value) {
        collector.record(value);
    }

    @Override
    public synchronized Snapshot snapshot() {
        return collector.snapshot();
    }
}
