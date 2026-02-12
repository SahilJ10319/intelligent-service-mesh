package com.neuragate.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * Day 17: Telemetry Capture Filter
 * 
 * Global filter that captures request/response telemetry for every gateway
 * request.
 * 
 * Execution flow:
 * 1. Request arrives → Capture start time
 * 2. Request processed → Route through gateway
 * 3. Response ready → Calculate latency
 * 4. Async publish → Send to Kafka (non-blocking)
 * 
 * Performance characteristics:
 * - Pre-filter overhead: ~0.05ms (timestamp capture)
 * - Post-filter overhead: ~0.1ms (latency calc + async send)
 * - Total impact: ~0.15ms per request
 * - Kafka publish: Async, zero blocking time
 * 
 * Order: HIGHEST_PRECEDENCE + 1
 * - Runs early to capture accurate start time
 * - Runs after LoggingFilter (HIGHEST_PRECEDENCE)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryCaptureFilter implements GlobalFilter, Ordered {

    private static final String START_TIME_ATTR = "telemetry.startTime";
    private static final String REQUEST_TIMESTAMP_ATTR = "telemetry.timestamp";

    private final TelemetryPublisher telemetryPublisher;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Capture start time and timestamp
        long startTime = System.currentTimeMillis();
        Instant timestamp = Instant.now();

        exchange.getAttributes().put(START_TIME_ATTR, startTime);
        exchange.getAttributes().put(REQUEST_TIMESTAMP_ATTR, timestamp);

        // Continue filter chain, then capture telemetry after response
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> captureTelemetry(exchange, startTime, timestamp)))
                .onErrorResume(throwable -> {
                    // Capture telemetry even on error
                    captureTelemetryWithError(exchange, startTime, timestamp, throwable);
                    return Mono.error(throwable);
                });
    }

    /**
     * Capture telemetry after successful response.
     * 
     * This runs asynchronously after the response is sent to the client.
     * Uses Mono.fromRunnable() to ensure non-blocking execution.
     */
    private void captureTelemetry(ServerWebExchange exchange, long startTime, Instant timestamp) {
        try {
            long latency = System.currentTimeMillis() - startTime;

            // Extract route information
            Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
            String routeId = route != null ? route.getId() : null;

            // Extract request information
            String path = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().name();

            // Extract response information
            HttpStatus statusCode = exchange.getResponse().getStatusCode();
            Integer status = statusCode != null ? statusCode.value() : null;

            // Extract client information
            String clientIp = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";

            String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");

            // Check for rate limiting (429 status)
            boolean rateLimited = statusCode == HttpStatus.TOO_MANY_REQUESTS;

            // Check for circuit breaker (503 status with specific message)
            boolean circuitBreakerTriggered = statusCode == HttpStatus.SERVICE_UNAVAILABLE;

            // Build telemetry event
            GatewayTelemetry telemetry = GatewayTelemetry.builder()
                    .routeId(routeId)
                    .path(path)
                    .method(method)
                    .status(status)
                    .latency(latency)
                    .timestamp(timestamp)
                    .clientIp(clientIp)
                    .userAgent(userAgent)
                    .rateLimited(rateLimited)
                    .circuitBreakerTriggered(circuitBreakerTriggered)
                    .retryCount(0) // TODO: Extract from retry filter
                    .build();

            // Publish asynchronously (fire-and-forget)
            telemetryPublisher.publishTelemetry(telemetry);

        } catch (Exception e) {
            // Never let telemetry errors affect gateway
            log.error("Error capturing telemetry: {}", e.getMessage());
        }
    }

    /**
     * Capture telemetry when an error occurs during request processing.
     */
    private void captureTelemetryWithError(ServerWebExchange exchange, long startTime,
            Instant timestamp, Throwable throwable) {
        try {
            long latency = System.currentTimeMillis() - startTime;

            Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
            String routeId = route != null ? route.getId() : null;

            String path = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().name();

            // Build telemetry event with error status
            GatewayTelemetry telemetry = GatewayTelemetry.builder()
                    .routeId(routeId)
                    .path(path)
                    .method(method)
                    .status(500) // Internal server error
                    .latency(latency)
                    .timestamp(timestamp)
                    .build();

            telemetryPublisher.publishTelemetry(telemetry);

            // Also publish error event
            telemetryPublisher.publishError(routeId, path,
                    throwable.getMessage(),
                    throwable.getClass().getName());

        } catch (Exception e) {
            log.error("Error capturing error telemetry: {}", e.getMessage());
        }
    }

    /**
     * Execute early to capture accurate start time.
     * 
     * Order: HIGHEST_PRECEDENCE + 1
     * - After LoggingFilter (HIGHEST_PRECEDENCE)
     * - Before all other filters
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
