package com.neuragate.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Day 10: Retry Event Logger
 * 
 * Logs retry attempts for observability and debugging.
 * This helps operators:
 * - Track transient failures
 * - Identify problematic services
 * - Monitor retry effectiveness
 * - Detect retry storms
 * 
 * Why log retry events?
 * - Visibility into transient failures
 * - Can detect patterns (e.g., always fails on 1st attempt)
 * - Helps tune retry configuration
 * - Provides audit trail for incidents
 */
@Slf4j
@Configuration
public class RetryEventLogger {

    private final RetryRegistry retryRegistry;

    public RetryEventLogger(RetryRegistry retryRegistry) {
        this.retryRegistry = retryRegistry;
    }

    /**
     * Register event listeners for all retry instances.
     * 
     * This runs after the Spring context is initialized and all
     * retry instances are registered.
     */
    @PostConstruct
    public void registerEventListeners() {
        log.info("Registering retry event listeners");

        // Listen to all retry instances (existing and future)
        retryRegistry.getAllRetries().forEach(this::registerEventListener);

        // Also register listener for retry instances created in the future
        retryRegistry.getEventPublisher()
                .onEntryAdded(event -> registerEventListener(event.getAddedEntry()));
    }

    /**
     * Register event listener for a specific retry instance.
     * 
     * @param retry The retry instance to monitor
     */
    private void registerEventListener(Retry retry) {
        String retryName = retry.getName();

        log.info("Registering event listener for retry instance: {}", retryName);

        // Listen to retry events
        retry.getEventPublisher()
                .onRetry(this::logRetryAttempt);

        // Listen to success after retry
        retry.getEventPublisher()
                .onSuccess(event -> log.info("✅ Retry '{}' succeeded after {} attempt(s)",
                        retryName, event.getNumberOfRetryAttempts()));

        // Listen to error (all retries exhausted)
        retry.getEventPublisher()
                .onError(event -> log.error("❌ Retry '{}' failed after {} attempt(s): {}",
                        retryName,
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getMessage()));

        // Listen to ignored errors (errors that don't trigger retry)
        retry.getEventPublisher()
                .onIgnoredError(event -> log.debug("Retry '{}' ignored error (not retryable): {}",
                        retryName, event.getLastThrowable().getMessage()));
    }

    /**
     * Log retry attempts with detailed information.
     * 
     * This is the most important event - it shows when retries are happening,
     * which indicates transient failures.
     * 
     * @param event The retry event
     */
    private void logRetryAttempt(RetryOnRetryEvent event) {
        String retryName = event.getName();
        int attemptNumber = event.getNumberOfRetryAttempts();
        long waitInterval = event.getWaitInterval().toMillis();
        String errorMessage = event.getLastThrowable().getMessage();
        String errorType = event.getLastThrowable().getClass().getSimpleName();

        // Log with different levels based on attempt number
        if (attemptNumber == 1) {
            // First retry - this is normal for transient failures
            log.warn("🔄 Retry '{}' - Attempt #{} after {}ms - {}: {}",
                    retryName, attemptNumber, waitInterval, errorType, errorMessage);
        } else if (attemptNumber == 2) {
            // Second retry - getting concerning
            log.warn("⚠️  Retry '{}' - Attempt #{} after {}ms - {}: {}",
                    retryName, attemptNumber, waitInterval, errorType, errorMessage);
        } else {
            // Third+ retry - this is serious
            log.error("🚨 Retry '{}' - Attempt #{} after {}ms - {}: {}",
                    retryName, attemptNumber, waitInterval, errorType, errorMessage);
        }

        // Additional context for debugging
        log.debug("Retry '{}' - Exception stack trace:", retryName, event.getLastThrowable());
    }
}
