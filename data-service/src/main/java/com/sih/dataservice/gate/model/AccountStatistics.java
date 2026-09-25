package com.sih.dataservice.gate.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

public class AccountStatistics {

    private long count = 0;
    private double mean = 0.0;
    private double m2 = 0.0; // Sum of squared differences from the mean (Welford's algorithm)
    private final Deque<Instant> recentTimestamps = new ArrayDeque<>();

    public synchronized void update(double amount, Instant timestamp, Duration velocityWindow) {
        count++;
        double delta = amount - mean;
        mean += delta / count;
        double delta2 = amount - mean;
        m2 += delta * delta2;

        if (timestamp != null) {
            recentTimestamps.addLast(timestamp);
            pruneTimestamps(timestamp, velocityWindow);
        }
    }

    public synchronized int getVelocity(Instant currentTimestamp, Duration velocityWindow) {
        if (currentTimestamp != null) {
            pruneTimestamps(currentTimestamp, velocityWindow);
        }
        return recentTimestamps.size();
    }

    private void pruneTimestamps(Instant now, Duration window) {
        if (now == null || window == null) return;
        Instant cutoff = now.minus(window);
        while (!recentTimestamps.isEmpty() && recentTimestamps.peekFirst().isBefore(cutoff)) {
            recentTimestamps.pollFirst();
        }
    }

    public synchronized double getStandardDeviation() {
        if (count < 2) {
            return 0.0;
        }
        return Math.sqrt(m2 / (count - 1));
    }

    public synchronized double calculateZScore(double amount) {
        double stdDev = getStandardDeviation();
        if (stdDev <= 0.0001) {
            return 0.0;
        }
        return Math.abs(amount - mean) / stdDev;
    }

    public synchronized long getCount() {
        return count;
    }

    public synchronized double getMean() {
        return mean;
    }
}
