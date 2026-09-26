package com.eugeneakimov;

import java.util.Arrays;
import java.util.Random;

public class Utils {
    private Utils() {
    }

    public static long computePercentile(long[] buckets, long count, double p) {
        var threshold = Math.ceil(p * count);
        long acc = 0;
        for (int i = 0; i < buckets.length; i++) {
            acc += buckets[i];
            if (acc >= threshold) {
                return i * 4L;
            }
        }
        return 0;
    }

    public static long[] generateZipf(
            int size,
            int maxLatency,
            long seed
    ) {
        var cdf = new double[maxLatency];
        double sum = 0.0;
        for (int k = 0; k < maxLatency; k++) {
            sum += 1.0 / Math.pow(k + 1, 1.15);
            cdf[k] = sum;
        }
        for (int k = 0; k < maxLatency; k++) {
            cdf[k] /= sum;
        }
        var random = new Random(seed);
        var values = new long[size];
        for (int i = 0; i < size; i++) {
            double x = random.nextDouble();
            int k = Arrays.binarySearch(cdf, x);
            if (k < 0) {
                k = -k - 1;
            }
            values[i] = k + 1;
        }
        return values;
    }

    public static void printBandwidth(double bandwidth) {
        IO.print("%.3f".formatted(bandwidth / 1_000_000));
    }
}
