package com.neuragate.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Day 19: Telemetry Logging Configuration
 * 
 * Scheduled reporter that logs telemetry statistics every 30 seconds.
 * Provides real-time visibility into gateway performance and traffic patterns.
 * 
 * Metrics reported:
 * - Buffer size and utilization
 * - Average latency
 * - Error rate
 * - Rate limit count
 * - Circuit breaker activations
 * 
 * This serves as:
 * - Health check for telemetry pipeline
 * - Quick performance overview
 * - Debugging aid during development
 * - Foundation for alerting rules
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class TelemetryLoggingConfig {

    private final MetricsBuffer metricsBuffer;

    /**
     * Log telemetry summary every 30 seconds.
     * 
     * Runs on Spring's scheduled task executor.
     * Non-blocking - doesn't affect gateway performance.
     */
    @Scheduled(fixedRate = 30000, initialDelay = 30000)
    public void logTelemetrySummary() {
        int bufferSize = metricsBuffer.getSize();

        if (bufferSize == 0) {
            log.info("📊 Telemetry Summary: No metrics in buffer");
            return;
        }

        double avgLatency = metricsBuffer.getAverageLatency();
        double errorRate = metricsBuffer.getErrorRate();
        double utilization = metricsBuffer.getUtilization();
        long rateLimitedCount = metricsBuffer.getRateLimitedCount();
        long circuitBreakerCount = metricsBuffer.getCircuitBreakerCount();

        long count2xx = metricsBuffer.getCountByStatus(200);
        long count4xx = metricsBuffer.getCountByStatus(404) +
                metricsBuffer.getCountByStatus(429);
        long count5xx = metricsBuffer.getCountByStatus(500) +
                metricsBuffer.getCountByStatus(503);

        log.info("""

                ╔════════════════════════════════════════════════════════════════╗
                ║              📊 TELEMETRY SUMMARY (Last 30s)                  ║
                ╠════════════════════════════════════════════════════════════════╣
                ║  Buffer:          {}/{} events ({:.1f}% full)
                ║  Avg Latency:     {:.2f}ms
                ║  Error Rate:      {:.2f}%
                ║
                ║  Status Codes:
                ║    ✅ 2xx:        {} requests
                ║    ⚠️  4xx:        {} requests
                ║    ❌ 5xx:        {} requests
                ║
                ║  Resilience:
                ║    🚦 Rate Limited:      {} requests
                ║    ⚡ Circuit Breaker:   {} activations
                ╚════════════════════════════════════════════════════════════════╝
                """,
                bufferSize,
                metricsBuffer.getCapacity(),
                utilization,
                avgLatency,
                errorRate,
                count2xx,
                count4xx,
                count5xx,
                rateLimitedCount,
                circuitBreakerCount);
    }

    /**
     * Log detailed metrics every 5 minutes.
     * 
     * Provides deeper insights into traffic patterns.
     */
    @Scheduled(fixedRate = 300000, initialDelay = 60000)
    public void logDetailedMetrics() {
        int bufferSize = metricsBuffer.getSize();

        if (bufferSize == 0) {
            return;
        }

        // Calculate percentile latencies
        var metrics = metricsBuffer.getRecentMetrics();
        var latencies = metrics.stream()
                .filter(m -> m.getLatency() != null)
                .mapToLong(GatewayTelemetry::getLatency)
                .sorted()
                .toArray();

        if (latencies.length == 0) {
            return;
        }

        long p50 = latencies[latencies.length / 2];
        long p95 = latencies[(int) (latencies.length * 0.95)];
        long p99 = latencies[(int) (latencies.length * 0.99)];
        long max = latencies[latencies.length - 1];

        log.info("""

                ╔════════════════════════════════════════════════════════════════╗
                ║           📈 DETAILED METRICS (Last 5 minutes)                ║
                ╠════════════════════════════════════════════════════════════════╣
                ║  Latency Percentiles:
                ║    P50 (median):  {}ms
                ║    P95:           {}ms
                ║    P99:           {}ms
                ║    Max:           {}ms
                ║
                ║  Total Requests:  {}
                ╚════════════════════════════════════════════════════════════════╝
                """,
                p50, p95, p99, max, bufferSize);
    }
}
