package com.neuragate.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Day 19: Telemetry Consumer
 * 
 * Kafka consumer that listens to gateway telemetry events and processes them.
 * This is the "listening" phase where raw events become actionable data.
 * 
 * Architecture:
 * - Consumes from gateway-telemetry topic
 * - Deserializes JSON to GatewayTelemetry objects
 * - Stores in MetricsBuffer for real-time analysis
 * - Enables AI-driven traffic pattern detection
 * 
 * Consumer Configuration:
 * - Group ID: neuragate-analytics
 * - Auto-offset reset: earliest (process all historical data)
 * - Concurrency: 3 (matches topic partition count)
 * - JSON deserialization: Automatic via Spring Kafka
 * 
 * Performance:
 * - Non-blocking: Runs on separate consumer threads
 * - High throughput: Can process 1000+ events/sec
 * - Fault tolerant: Auto-commits offsets after processing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryConsumer {

    private final MetricsBuffer metricsBuffer;
    private final AnomalyDetector anomalyDetector;

    /**
     * Consume telemetry events from Kafka.
     * 
     * This method is called automatically by Spring Kafka for each message.
     * Runs on dedicated consumer thread pool (separate from gateway threads).
     * 
     * @param telemetry Deserialized telemetry event
     * @param partition Kafka partition this message came from
     * @param offset    Kafka offset of this message
     */
    @KafkaListener(topics = "gateway-telemetry", groupId = "neuragate-analytics", concurrency = "3", containerFactory = "kafkaListenerContainerFactory")
    public void consumeTelemetry(
            @Payload GatewayTelemetry telemetry,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        try {
            // Log telemetry event at debug level
            log.debug("📥 Consumed telemetry [partition={}, offset={}]: {} {} -> {} ({}ms) [{}]",
                    partition,
                    offset,
                    telemetry.getMethod(),
                    telemetry.getPath(),
                    telemetry.getStatus(),
                    telemetry.getLatency(),
                    telemetry.getCorrelationId());

            // Store in metrics buffer for real-time analysis
            metricsBuffer.addMetric(telemetry);

            // Day 21: Analyze for anomalies
            anomalyDetector.analyze(telemetry);

            // Log info for important events
            if (telemetry.getStatus() != null && telemetry.getStatus() >= 500) {
                log.warn("🚨 Error event consumed: {} {} -> {} [{}]",
                        telemetry.getMethod(),
                        telemetry.getPath(),
                        telemetry.getStatus(),
                        telemetry.getCorrelationId());
            }

            if (Boolean.TRUE.equals(telemetry.getRateLimited())) {
                log.info("🚦 Rate limit event consumed: {} {} [{}]",
                        telemetry.getMethod(),
                        telemetry.getPath(),
                        telemetry.getCorrelationId());
            }

            if (Boolean.TRUE.equals(telemetry.getCircuitBreakerTriggered())) {
                log.warn("⚡ Circuit breaker event consumed: {} {} [{}]",
                        telemetry.getMethod(),
                        telemetry.getPath(),
                        telemetry.getCorrelationId());
            }

        } catch (Exception e) {
            // Log error but don't throw (prevents consumer from stopping)
            log.error("Error processing telemetry event: {}", e.getMessage(), e);
        }
    }

    /**
     * Consume error events from Kafka.
     * 
     * Separate listener for error events to enable different processing logic.
     * 
     * @param errorEvent Error event payload
     * @param partition  Kafka partition
     * @param offset     Kafka offset
     */
    @KafkaListener(topics = "gateway-errors", groupId = "neuragate-analytics", concurrency = "2")
    public void consumeError(
            @Payload String errorEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.error("🚨 Error event consumed [partition={}, offset={}]: {}",
                partition, offset, errorEvent);

        // TODO: Send to alerting system (PagerDuty, Slack, etc.)
        // TODO: Store in error analytics database
    }

    /**
     * Consume route lifecycle events from Kafka.
     * 
     * Tracks route creation, updates, and deletions for audit trail.
     * 
     * @param routeEvent Route event payload
     * @param partition  Kafka partition
     * @param offset     Kafka offset
     */
    @KafkaListener(topics = "gateway-routes", groupId = "neuragate-analytics", concurrency = "1")
    public void consumeRouteEvent(
            @Payload String routeEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("🛣️  Route event consumed [partition={}, offset={}]: {}",
                partition, offset, routeEvent);

        // TODO: Store in route history database
        // TODO: Trigger route validation checks
    }
}
