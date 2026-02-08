package com.neuragate.mock;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Day 12: Chaos Engineering Settings
 * 
 * In-memory storage for chaos configuration.
 * Allows dynamic control of failure injection and latency simulation
 * without restarting the service.
 * 
 * Thread-safe using atomic operations.
 */
@Slf4j
@Data
@Component
public class ChaosSettings {

    /**
     * Percentage of requests that should fail (0-100)
     */
    private final AtomicInteger failureRate = new AtomicInteger(0);

    /**
     * Artificial latency to add to responses (in milliseconds)
     */
    private final AtomicInteger latencyMs = new AtomicInteger(0);

    /**
     * Set the failure rate percentage.
     * 
     * @param rate Failure rate (0-100)
     */
    public void setFailureRate(int rate) {
        if (rate < 0 || rate > 100) {
            throw new IllegalArgumentException("Failure rate must be between 0 and 100");
        }
        int oldRate = this.failureRate.getAndSet(rate);
        log.info("🎛️  Chaos Settings Updated: Failure rate changed from {}% to {}%", oldRate, rate);
    }

    /**
     * Get the current failure rate.
     * 
     * @return Current failure rate percentage
     */
    public int getFailureRate() {
        return failureRate.get();
    }

    /**
     * Set the artificial latency.
     * 
     * @param latency Latency in milliseconds
     */
    public void setLatencyMs(int latency) {
        if (latency < 0) {
            throw new IllegalArgumentException("Latency must be non-negative");
        }
        int oldLatency = this.latencyMs.getAndSet(latency);
        log.info("🎛️  Chaos Settings Updated: Latency changed from {}ms to {}ms", oldLatency, latency);
    }

    /**
     * Get the current latency setting.
     * 
     * @return Current latency in milliseconds
     */
    public int getLatencyMs() {
        return latencyMs.get();
    }

    /**
     * Reset all chaos settings to defaults (healthy state).
     */
    public void reset() {
        int oldRate = failureRate.getAndSet(0);
        int oldLatency = latencyMs.getAndSet(0);
        log.info("🔄 Chaos Settings Reset: Failure rate {}% → 0%, Latency {}ms → 0ms", oldRate, oldLatency);
    }

    /**
     * Check if chaos mode is active.
     * 
     * @return true if any chaos settings are configured
     */
    public boolean isChaosActive() {
        return failureRate.get() > 0 || latencyMs.get() > 0;
    }
}
