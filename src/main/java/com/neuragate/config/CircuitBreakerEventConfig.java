package com.neuragate.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Day 9: Circuit Breaker Event Configuration
 * 
 * Logs circuit breaker state transitions for observability.
 * This allows operators to:
 * - Track when services start failing (CLOSED → OPEN)
 * - Monitor recovery attempts (OPEN → HALF_OPEN)
 * - Verify successful recovery (HALF_OPEN → CLOSED)
 * - Detect recurring failures (HALF_OPEN → OPEN)
 * 
 * Why event-based logging?
 * - Real-time visibility into circuit breaker behavior
 * - Can be integrated with alerting systems
 * - Helps diagnose downstream service issues
 * - Provides audit trail for incidents
 */
@Slf4j
@Configuration
public class CircuitBreakerEventConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerEventConfig(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    /**
     * Register event listeners for all circuit breakers.
     * 
     * This runs after the Spring context is initialized and all
     * circuit breakers are registered.
     */
    @PostConstruct
    public void registerEventListeners() {
        log.info("Registering circuit breaker event listeners");

        // Listen to all circuit breakers (existing and future)
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::registerEventListener);

        // Also register listener for circuit breakers created in the future
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> registerEventListener(event.getAddedEntry()));
    }

    /**
     * Register event listener for a specific circuit breaker.
     * 
     * @param circuitBreaker The circuit breaker to monitor
     */
    private void registerEventListener(CircuitBreaker circuitBreaker) {
        String cbName = circuitBreaker.getName();

        log.info("Registering event listener for circuit breaker: {}", cbName);

        // Listen to state transition events
        circuitBreaker.getEventPublisher()
                .onStateTransition(this::logStateTransition);

        // Listen to success events (for debugging)
        circuitBreaker.getEventPublisher()
                .onSuccess(event -> log.debug("Circuit breaker '{}' - Successful call", cbName));

        // Listen to error events
        circuitBreaker.getEventPublisher()
                .onError(event -> log.warn("Circuit breaker '{}' - Failed call: {}",
                        cbName, event.getThrowable().getMessage()));

        // Listen to ignored error events
        circuitBreaker.getEventPublisher()
                .onIgnoredError(event -> log.debug("Circuit breaker '{}' - Ignored error: {}",
                        cbName, event.getThrowable().getMessage()));
    }

    /**
     * Log circuit breaker state transitions.
     * 
     * This is the most important event - it shows when the circuit breaker
     * changes state, which indicates service health changes.
     * 
     * @param event The state transition event
     */
    private void logStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        String cbName = event.getCircuitBreakerName();
        String fromState = event.getStateTransition().getFromState().toString();
        String toState = event.getStateTransition().getToState().toString();

        // Log at different levels based on severity
        switch (toState) {
            case "OPEN":
                // Service is failing - this is critical
                log.error("⚠️  Circuit breaker '{}' opened: {} → {} (Service is failing, using fallback)",
                        cbName, fromState, toState);
                break;

            case "HALF_OPEN":
                // Testing recovery - this is informational
                log.warn("🔄 Circuit breaker '{}' half-open: {} → {} (Testing service recovery)",
                        cbName, fromState, toState);
                break;

            case "CLOSED":
                // Service recovered - this is good news
                log.info("✅ Circuit breaker '{}' closed: {} → {} (Service recovered)",
                        cbName, fromState, toState);
                break;

            default:
                log.info("Circuit breaker '{}' state transition: {} → {}",
                        cbName, fromState, toState);
        }
    }
}
