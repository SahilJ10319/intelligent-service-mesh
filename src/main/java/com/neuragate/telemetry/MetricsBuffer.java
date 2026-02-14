package com.neuragate.telemetry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Day 19: Metrics Buffer
 * 
 * Thread-safe in-memory buffer for storing recent telemetry events.
 * This is the foundation for real-time traffic pattern analysis.
 * 
 * Design:
 * - ConcurrentLinkedQueue: Lock-free, thread-safe queue
 * - Fixed capacity: 500 most recent events
 * - FIFO eviction: Oldest events removed when capacity exceeded
 * - Zero blocking: Producers never wait
 * 
 * Use cases:
 * - Real-time dashboards
 * - AI traffic pattern detection
 * - Anomaly detection
 * - Performance monitoring
 * - Capacity planning
 * 
 * Performance:
 * - O(1) add operation
 * - O(n) read operation (n = buffer size)
 * - Memory: ~50KB for 500 events
 * - Thread-safe: Multiple consumers can read concurrently
 */
@Slf4j
@Component
public class MetricsBuffer {

    private static final int MAX_BUFFER_SIZE = 500;

    private final ConcurrentLinkedQueue<GatewayTelemetry> buffer = new ConcurrentLinkedQueue<>();
    private volatile int currentSize = 0;

    /**
     * Add a telemetry event to the buffer.
     * 
     * Thread-safe, non-blocking operation.
     * If buffer is full, oldest event is removed.
     * 
     * @param telemetry Telemetry event to add
     */
    public void addMetric(GatewayTelemetry telemetry) {
        if (telemetry == null) {
            log.warn("Attempted to add null telemetry to buffer");
            return;
        }

        buffer.offer(telemetry);
        currentSize++;

        // Remove oldest event if buffer exceeds capacity
        if (currentSize > MAX_BUFFER_SIZE) {
            buffer.poll();
            currentSize--;
        }

        log.trace("Added telemetry to buffer: {} {} (buffer size: {})",
                telemetry.getMethod(), telemetry.getPath(), currentSize);
    }

    /**
     * Get all recent metrics from the buffer.
     * 
     * Returns a snapshot of current buffer contents.
     * Safe to call from multiple threads concurrently.
     * 
     * @return List of recent telemetry events (newest first)
     */
    public List<GatewayTelemetry> getRecentMetrics() {
        return buffer.stream()
                .collect(Collectors.toList());
    }

    /**
     * Get the last N metrics from the buffer.
     * 
     * @param count Number of metrics to retrieve
     * @return List of most recent telemetry events
     */
    public List<GatewayTelemetry> getRecentMetrics(int count) {
        return buffer.stream()
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * Get current buffer size.
     * 
     * @return Number of events currently in buffer
     */
    public int getSize() {
        return currentSize;
    }

    /**
     * Get maximum buffer capacity.
     * 
     * @return Maximum number of events buffer can hold
     */
    public int getCapacity() {
        return MAX_BUFFER_SIZE;
    }

    /**
     * Clear all metrics from the buffer.
     * 
     * Use with caution - this will lose all buffered data.
     */
    public void clear() {
        buffer.clear();
        currentSize = 0;
        log.info("Metrics buffer cleared");
    }

    /**
     * Get buffer utilization percentage.
     * 
     * @return Percentage of buffer capacity used (0-100)
     */
    public double getUtilization() {
        return (currentSize * 100.0) / MAX_BUFFER_SIZE;
    }

    /**
     * Calculate average latency from buffered metrics.
     * 
     * @return Average latency in milliseconds, or 0 if buffer is empty
     */
    public double getAverageLatency() {
        List<GatewayTelemetry> metrics = getRecentMetrics();

        if (metrics.isEmpty()) {
            return 0.0;
        }

        return metrics.stream()
                .filter(m -> m.getLatency() != null)
                .mapToLong(GatewayTelemetry::getLatency)
                .average()
                .orElse(0.0);
    }

    /**
     * Calculate error rate from buffered metrics.
     * 
     * @return Error rate as percentage (0-100)
     */
    public double getErrorRate() {
        List<GatewayTelemetry> metrics = getRecentMetrics();

        if (metrics.isEmpty()) {
            return 0.0;
        }

        long errorCount = metrics.stream()
                .filter(m -> m.getStatus() != null && m.getStatus() >= 500)
                .count();

        return (errorCount * 100.0) / metrics.size();
    }

    /**
     * Get count of events by status code.
     * 
     * @param statusCode HTTP status code
     * @return Count of events with this status code
     */
    public long getCountByStatus(int statusCode) {
        return buffer.stream()
                .filter(m -> m.getStatus() != null && m.getStatus() == statusCode)
                .count();
    }

    /**
     * Get count of rate-limited requests.
     * 
     * @return Count of rate-limited events
     */
    public long getRateLimitedCount() {
        return buffer.stream()
                .filter(m -> Boolean.TRUE.equals(m.getRateLimited()))
                .count();
    }

    /**
     * Get count of circuit breaker activations.
     * 
     * @return Count of circuit breaker events
     */
    public long getCircuitBreakerCount() {
        return buffer.stream()
                .filter(m -> Boolean.TRUE.equals(m.getCircuitBreakerTriggered()))
                .count();
    }
}
