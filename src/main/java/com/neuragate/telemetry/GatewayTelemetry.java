package com.neuragate.telemetry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Day 16: Gateway Telemetry Event
 * 
 * Immutable data structure representing a single gateway request/response
 * cycle.
 * This is the core telemetry payload sent to Kafka for observability.
 * 
 * Fields:
 * - routeId: Which route handled this request
 * - path: Request URI path
 * - method: HTTP method (GET, POST, etc.)
 * - status: HTTP response status code
 * - latency: Total request processing time in milliseconds
 * - timestamp: When the request was received
 * - clientIp: Client IP address (for traffic analysis)
 * - userAgent: Client user agent (for client analytics)
 * 
 * This data enables:
 * - Real-time latency monitoring
 * - Error rate tracking
 * - Route performance analysis
 * - Traffic pattern detection
 * - Client behavior analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayTelemetry {

    /**
     * Route ID that processed this request.
     * Null if no route matched (404 scenarios).
     */
    private String routeId;

    /**
     * Request URI path.
     * Example: /api/inventory/123
     */
    private String path;

    /**
     * HTTP method.
     * Example: GET, POST, PUT, DELETE
     */
    private String method;

    /**
     * HTTP response status code.
     * Example: 200, 404, 500, 429
     */
    private Integer status;

    /**
     * Total request latency in milliseconds.
     * Measured from request arrival to response completion.
     */
    private Long latency;

    /**
     * Request timestamp (ISO-8601 format).
     * When the request was received by the gateway.
     */
    private Instant timestamp;

    /**
     * Correlation ID for distributed tracing.
     * Unique identifier that travels through the entire request chain.
     * Enables end-to-end tracing across multiple services.
     */
    private String correlationId;

    /**
     * Client IP address.
     * Used for traffic analysis and rate limiting insights.
     */
    private String clientIp;

    /**
     * Client user agent.
     * Used for client analytics and debugging.
     */
    private String userAgent;

    /**
     * Whether rate limiting was applied.
     * Helps track rate limit effectiveness.
     */
    private Boolean rateLimited;

    /**
     * Whether circuit breaker was triggered.
     * Helps track resilience pattern activation.
     */
    private Boolean circuitBreakerTriggered;

    /**
     * Number of retry attempts.
     * Helps track retry pattern effectiveness.
     */
    private Integer retryCount;
}
