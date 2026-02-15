package com.neuragate.telemetry;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Day 20: Metrics Exporter
 * 
 * Bridges MetricsBuffer with Micrometer's MeterRegistry for Prometheus export.
 * Exposes custom gauges that Prometheus can scrape via /actuator/prometheus.
 * 
 * Metrics exposed:
 * - gateway.latency.average: Average request latency in milliseconds
 * - gateway.error.rate: Error rate as percentage (0-100)
 * - gateway.buffer.size: Current metrics buffer size
 * - gateway.buffer.utilization: Buffer utilization percentage
 * - gateway.requests.rate_limited: Count of rate-limited requests
 * - gateway.requests.circuit_breaker: Count of circuit breaker activations
 * - gateway.anomalies.total: Total anomalies detected
 * 
 * Architecture:
 * - Gauges: Real-time values computed on each scrape
 * - Non-blocking: Metrics calculation is fast (O(n) where n = buffer size)
 * - Thread-safe: MetricsBuffer is concurrent-safe
 * 
 * Prometheus scrape interval: 15 seconds (configurable)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsExporter {

    private final MeterRegistry meterRegistry;
    private final MetricsBuffer metricsBuffer;
    private final AnomalyDetector anomalyDetector;

    /**
     * Register custom gauges with Micrometer after bean initialization.
     * 
     * Gauges are lazy - they compute values on-demand when Prometheus scrapes.
     */
    @PostConstruct
    public void registerMetrics() {
        log.info("📊 Registering custom Prometheus metrics");

        // Average latency gauge
        Gauge.builder("gateway.latency.average", metricsBuffer, MetricsBuffer::getAverageLatency)
                .description("Average request latency in milliseconds")
                .baseUnit("milliseconds")
                .tag("component", "gateway")
                .register(meterRegistry);

        // Error rate gauge
        Gauge.builder("gateway.error.rate", metricsBuffer, MetricsBuffer::getErrorRate)
                .description("Error rate as percentage (0-100)")
                .baseUnit("percent")
                .tag("component", "gateway")
                .register(meterRegistry);

        // Buffer size gauge
        Gauge.builder("gateway.buffer.size", metricsBuffer, MetricsBuffer::getSize)
                .description("Current metrics buffer size")
                .baseUnit("events")
                .tag("component", "gateway")
                .register(meterRegistry);

        // Buffer utilization gauge
        Gauge.builder("gateway.buffer.utilization", metricsBuffer, MetricsBuffer::getUtilization)
                .description("Buffer utilization percentage (0-100)")
                .baseUnit("percent")
                .tag("component", "gateway")
                .register(meterRegistry);

        // Rate limited requests gauge
        Gauge.builder("gateway.requests.rate_limited", metricsBuffer, MetricsBuffer::getRateLimitedCount)
                .description("Count of rate-limited requests in buffer")
                .baseUnit("requests")
                .tag("component", "gateway")
                .register(meterRegistry);

        // Circuit breaker activations gauge
        Gauge.builder("gateway.requests.circuit_breaker", metricsBuffer, MetricsBuffer::getCircuitBreakerCount)
                .description("Count of circuit breaker activations in buffer")
                .baseUnit("activations")
                .tag("component", "gateway")
                .register(meterRegistry);

        // Day 21: Anomaly count gauge
        Gauge.builder("gateway.anomalies.total", anomalyDetector, AnomalyDetector::getAnomalyCount)
                .description("Total number of anomalies detected")
                .baseUnit("anomalies")
                .tag("component", "gateway")
                .register(meterRegistry);

        // Baseline latency gauge
        Gauge.builder("gateway.latency.baseline", anomalyDetector, AnomalyDetector::getBaselineLatency)
                .description("Baseline latency for anomaly detection")
                .baseUnit("milliseconds")
                .tag("component", "gateway")
                .register(meterRegistry);

        // Status code gauges
        Gauge.builder("gateway.requests.status.2xx", metricsBuffer,
                buffer -> buffer.getCountByStatus(200))
                .description("Count of 2xx responses")
                .baseUnit("requests")
                .tag("component", "gateway")
                .tag("status_class", "2xx")
                .register(meterRegistry);

        Gauge.builder("gateway.requests.status.4xx", metricsBuffer,
                buffer -> buffer.getCountByStatus(404) + buffer.getCountByStatus(429))
                .description("Count of 4xx responses")
                .baseUnit("requests")
                .tag("component", "gateway")
                .tag("status_class", "4xx")
                .register(meterRegistry);

        Gauge.builder("gateway.requests.status.5xx", metricsBuffer,
                buffer -> buffer.getCountByStatus(500) + buffer.getCountByStatus(503))
                .description("Count of 5xx responses")
                .baseUnit("requests")
                .tag("component", "gateway")
                .tag("status_class", "5xx")
                .register(meterRegistry);

        log.info("✅ Registered 11 custom Prometheus metrics");
        log.info("📡 Metrics available at: /actuator/prometheus");
    }
}
