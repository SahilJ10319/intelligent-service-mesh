package com.neuragate.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Day 16: Telemetry Publisher Service
 * 
 * Asynchronous, non-blocking service for publishing telemetry events to Kafka.
 * 
 * Design principles:
 * - Zero latency impact: All Kafka operations are async
 * - Fire-and-forget: Telemetry failures don't affect gateway
 * - Reactive: Uses CompletableFuture for non-blocking I/O
 * - Resilient: Logs failures but never throws exceptions
 * 
 * Performance characteristics:
 * - Async send: ~0.1ms overhead (vs 5-10ms for sync)
 * - Batching: Kafka batches messages for efficiency
 * - Compression: GZIP reduces network overhead
 * - Buffer: 32MB buffer prevents blocking on backpressure
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryPublisher {

    private static final String TELEMETRY_TOPIC = "gateway-telemetry";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish telemetry event to Kafka asynchronously.
     * 
     * This method is completely non-blocking:
     * 1. Immediately returns CompletableFuture
     * 2. Kafka send happens on separate thread pool
     * 3. Success/failure handled asynchronously
     * 4. Never blocks gateway request processing
     * 
     * @param telemetry The telemetry event to publish
     * @return CompletableFuture that completes when send finishes
     */
    public CompletableFuture<SendResult<String, Object>> publishTelemetry(GatewayTelemetry telemetry) {
        // Use route ID as partition key for even distribution
        String key = telemetry.getRouteId() != null ? telemetry.getRouteId() : "unknown";

        // Async send - returns immediately
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(TELEMETRY_TOPIC, key, telemetry);

        // Handle success/failure asynchronously (doesn't block)
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                // Log error but don't propagate (fire-and-forget)
                log.error("Failed to publish telemetry for route {}: {}",
                        telemetry.getRouteId(), ex.getMessage());
            } else {
                // Log success at debug level (too verbose for info)
                log.debug("📊 Published telemetry: {} {} -> {} ({}ms)",
                        telemetry.getMethod(),
                        telemetry.getPath(),
                        telemetry.getStatus(),
                        telemetry.getLatency());
            }
        });

        return future;
    }

    /**
     * Publish error event to Kafka asynchronously.
     * 
     * Separate topic for errors allows different retention and alerting.
     * 
     * @param routeId      Route that encountered the error
     * @param path         Request path
     * @param errorMessage Error message
     * @param stackTrace   Stack trace (optional)
     */
    public void publishError(String routeId, String path, String errorMessage, String stackTrace) {
        ErrorEvent error = ErrorEvent.builder()
                .routeId(routeId)
                .path(path)
                .errorMessage(errorMessage)
                .stackTrace(stackTrace)
                .timestamp(java.time.Instant.now())
                .build();

        kafkaTemplate.send("gateway-errors", routeId, error)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish error event: {}", ex.getMessage());
                    } else {
                        log.debug("🚨 Published error event for route {}", routeId);
                    }
                });
    }

    /**
     * Publish route lifecycle event to Kafka asynchronously.
     * 
     * @param routeId         Route ID
     * @param operation       CREATE, UPDATE, or DELETE
     * @param routeDefinition Route definition (JSON)
     */
    public void publishRouteEvent(String routeId, String operation, String routeDefinition) {
        RouteEvent event = RouteEvent.builder()
                .routeId(routeId)
                .operation(operation)
                .routeDefinition(routeDefinition)
                .timestamp(java.time.Instant.now())
                .build();

        kafkaTemplate.send("gateway-routes", routeId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish route event: {}", ex.getMessage());
                    } else {
                        log.info("🛣️  Published route event: {} {}", operation, routeId);
                    }
                });
    }

    // Inner classes for error and route events
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class ErrorEvent {
        private String routeId;
        private String path;
        private String errorMessage;
        private String stackTrace;
        private java.time.Instant timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class RouteEvent {
        private String routeId;
        private String operation;
        private String routeDefinition;
        private java.time.Instant timestamp;
    }
}
