package com.neuragate.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Day 8: Fallback controller for circuit breaker degraded responses.
 * 
 * When a downstream service is unhealthy and the circuit breaker opens,
 * requests are redirected here instead of timing out or failing completely.
 * 
 * This provides:
 * - Graceful degradation (fast failure instead of timeout)
 * - User-friendly error messages
 * - Reduced load on failing services
 * - Observability (logged fallback activations)
 * 
 * Why reactive (Mono)?
 * - Consistent with Spring Cloud Gateway's reactive stack
 * - Non-blocking, preserves Virtual Thread benefits
 * - Can be composed with other reactive operations
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /**
     * Generic fallback endpoint for circuit breaker.
     * 
     * Returns a 503 Service Unavailable with a helpful message.
     * This is triggered when:
     * - Circuit breaker is OPEN (too many failures)
     * - Circuit breaker is HALF_OPEN and test request fails
     */
    @GetMapping("/message")
    public Mono<ResponseEntity<Map<String, Object>>> fallbackMessage() {
        log.warn("Circuit breaker fallback activated - service temporarily unavailable");

        Map<String, Object> response = Map.of(
                "status", "degraded",
                "message", "Service temporarily unavailable. Please try again later.",
                "timestamp", Instant.now().toString(),
                "reason", "Circuit breaker is open due to high failure rate");

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response));
    }

    /**
     * Fallback for backend services specifically.
     * 
     * Provides more specific messaging for backend service failures.
     */
    @GetMapping("/backend")
    public Mono<ResponseEntity<Map<String, Object>>> backendFallback() {
        log.warn("Backend service circuit breaker activated");

        Map<String, Object> response = Map.of(
                "status", "degraded",
                "message",
                "Backend service is currently experiencing issues. Using cached data or degraded functionality.",
                "timestamp", Instant.now().toString(),
                "service", "backend",
                "action", "Circuit breaker protection active");

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response));
    }

    /**
     * Fallback for critical services.
     * 
     * Critical services get a different message emphasizing the temporary nature.
     */
    @GetMapping("/critical")
    public Mono<ResponseEntity<Map<String, Object>>> criticalFallback() {
        log.error("Critical service circuit breaker activated - immediate attention required");

        Map<String, Object> response = Map.of(
                "status", "critical_degraded",
                "message", "A critical service is temporarily unavailable. Our team has been notified.",
                "timestamp", Instant.now().toString(),
                "service", "critical",
                "action", "Automatic recovery in progress");

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response));
    }
}
