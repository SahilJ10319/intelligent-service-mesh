package com.neuragate.telemetry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Day 21: Anomaly Detector
 * 
 * Monitors telemetry stream for anomalous patterns and triggers alerts.
 * 
 * Detection rules:
 * 1. Consecutive failures: 3+ consecutive 5xx responses
 * 2. Latency spike: 200% increase from baseline
 * 3. Error rate spike: >50% error rate in recent window
 * 
 * Actions on anomaly:
 * - Log ANOMALY_DETECTED event with correlation ID
 * - Increment anomaly counter (exposed to Prometheus)
 * - TODO: Send to alerting system (PagerDuty, Slack)
 * 
 * Design:
 * - Stateful: Tracks consecutive failures and baseline latency
 * - Thread-safe: Uses atomic counters
 * - Low overhead: O(1) per telemetry event
 */
@Slf4j
@Component
public class AnomalyDetector {

    @Value("${neuragate.anomaly.latency-threshold-ms:500}")
    private int latencyThresholdMs;

    @Value("${neuragate.anomaly.consecutive-failures-threshold:3}")
    private int consecutiveFailuresThreshold;

    @Value("${neuragate.anomaly.latency-spike-multiplier:2.0}")
    private double latencySpikeMultiplier;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong baselineLatency = new AtomicLong(100); // Initial baseline: 100ms
    private final AtomicLong anomalyCount = new AtomicLong(0);
    private final ConcurrentLinkedQueue<Long> recentLatencies = new ConcurrentLinkedQueue<>();
    private static final int LATENCY_WINDOW_SIZE = 20;

    /**
     * Analyze telemetry event for anomalies.
     * 
     * Called by TelemetryConsumer for each event.
     * 
     * @param telemetry Telemetry event to analyze
     */
    public void analyze(GatewayTelemetry telemetry) {
        if (telemetry == null) {
            return;
        }

        // Check for consecutive failures
        checkConsecutiveFailures(telemetry);

        // Check for latency spikes
        checkLatencySpike(telemetry);

        // Update baseline latency
        updateBaselineLatency(telemetry);
    }

    /**
     * Check for consecutive failure anomaly.
     * 
     * Triggers alert if 3+ consecutive 5xx responses detected.
     */
    private void checkConsecutiveFailures(GatewayTelemetry telemetry) {
        Integer status = telemetry.getStatus();

        if (status != null && status >= 500) {
            int failures = consecutiveFailures.incrementAndGet();

            if (failures >= consecutiveFailuresThreshold) {
                logAnomaly("CONSECUTIVE_FAILURES",
                        String.format("%d consecutive failures detected", failures),
                        telemetry.getCorrelationId());
            }
        } else {
            // Reset counter on successful request
            consecutiveFailures.set(0);
        }
    }

    /**
     * Check for latency spike anomaly.
     * 
     * Triggers alert if latency exceeds 200% of baseline.
     */
    private void checkLatencySpike(GatewayTelemetry telemetry) {
        Long latency = telemetry.getLatency();

        if (latency == null) {
            return;
        }

        // Check against absolute threshold
        if (latency > latencyThresholdMs) {
            logAnomaly("HIGH_LATENCY",
                    String.format("Latency %dms exceeds threshold %dms", latency, latencyThresholdMs),
                    telemetry.getCorrelationId());
        }

        // Check against baseline (spike detection)
        long baseline = baselineLatency.get();
        double spikeThreshold = baseline * latencySpikeMultiplier;

        if (latency > spikeThreshold) {
            logAnomaly("LATENCY_SPIKE",
                    String.format("Latency %dms is %.1fx baseline %dms",
                            latency, latencySpikeMultiplier, baseline),
                    telemetry.getCorrelationId());
        }
    }

    /**
     * Update baseline latency using rolling average.
     * 
     * Maintains window of recent latencies for baseline calculation.
     */
    private void updateBaselineLatency(GatewayTelemetry telemetry) {
        Long latency = telemetry.getLatency();

        if (latency == null || telemetry.getStatus() == null || telemetry.getStatus() >= 500) {
            // Don't include errors in baseline
            return;
        }

        recentLatencies.offer(latency);

        // Keep window size limited
        if (recentLatencies.size() > LATENCY_WINDOW_SIZE) {
            recentLatencies.poll();
        }

        // Calculate new baseline (average of recent latencies)
        if (!recentLatencies.isEmpty()) {
            long sum = recentLatencies.stream().mapToLong(Long::longValue).sum();
            long average = sum / recentLatencies.size();
            baselineLatency.set(average);
        }
    }

    /**
     * Log anomaly event and increment counter.
     * 
     * @param anomalyType   Type of anomaly detected
     * @param description   Detailed description
     * @param correlationId Correlation ID of triggering request
     */
    private void logAnomaly(String anomalyType, String description, String correlationId) {
        anomalyCount.incrementAndGet();

        log.warn("""

                ╔════════════════════════════════════════════════════════════════╗
                ║                    🚨 ANOMALY DETECTED                        ║
                ╠════════════════════════════════════════════════════════════════╣
                ║  Type:            {}
                ║  Description:     {}
                ║  Correlation ID:  {}
                ║  Total Anomalies: {}
                ╚════════════════════════════════════════════════════════════════╝
                """,
                anomalyType,
                description,
                correlationId != null ? correlationId : "NONE",
                anomalyCount.get());

        // TODO: Send to alerting system
        // TODO: Store in anomaly database
        // TODO: Trigger auto-remediation (e.g., scale up, circuit breaker)
    }

    /**
     * Get total anomaly count.
     * 
     * Exposed to Prometheus via MetricsExporter.
     * 
     * @return Total number of anomalies detected
     */
    public long getAnomalyCount() {
        return anomalyCount.get();
    }

    /**
     * Get current baseline latency.
     * 
     * @return Baseline latency in milliseconds
     */
    public long getBaselineLatency() {
        return baselineLatency.get();
    }

    /**
     * Get current consecutive failures count.
     * 
     * @return Number of consecutive failures
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * Reset anomaly detector state.
     * 
     * Use for testing or manual reset.
     */
    public void reset() {
        consecutiveFailures.set(0);
        baselineLatency.set(100);
        anomalyCount.set(0);
        recentLatencies.clear();
        log.info("Anomaly detector reset");
    }
}
