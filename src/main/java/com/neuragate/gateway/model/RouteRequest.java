package com.neuragate.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for route creation/update requests via the Admin API.
 * 
 * This is a simplified view of RouteDefinition that only includes
 * fields that can be set by administrators. System-managed fields
 * like lastModified are excluded.
 * 
 * Why a separate DTO instead of reusing RouteDefinition?
 * - Clear separation between API contract and internal model
 * - Prevents clients from setting system-managed fields
 * - Easier to evolve API independently of storage model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteRequest {

    /**
     * Unique identifier for this route.
     * Format: service-name-version (e.g., "user-service-v1")
     */
    private String id;

    /**
     * URI of the downstream service.
     * Supports http://, https://, lb:// (load-balanced), and ws:// schemes.
     * Example: "http://user-service:8080"
     */
    private String uri;

    /**
     * Path predicate for matching incoming requests.
     * Example: "/api/users/**"
     */
    private String path;

    /**
     * Circuit breaker name to apply to this route.
     * References a Resilience4j circuit breaker configuration.
     * If null, no circuit breaker is applied.
     */
    private String circuitBreakerName;

    /**
     * Priority for route matching. Lower values have higher priority.
     * Default: 0
     */
    @Builder.Default
    private int order = 0;

    /**
     * Whether this route is currently active.
     * Default: true
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * Whether this is a critical route that should be cached in-memory
     * as a fallback if Redis becomes unavailable.
     * Default: false
     */
    @Builder.Default
    private boolean critical = false;

    /**
     * Additional metadata for future extensibility.
     * Can include: expected_latency_ms, traffic_weight, canary_percentage, etc.
     */
    private Map<String, String> metadata;
}
