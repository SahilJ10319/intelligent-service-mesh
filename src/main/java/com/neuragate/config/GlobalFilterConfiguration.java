package com.neuragate.config;

import com.neuragate.filter.LoggingFilter;
import com.neuragate.filter.RateLimitResponseFilter;
import com.neuragate.telemetry.TelemetryCaptureFilter;
import com.neuragate.telemetry.TelemetryPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Day 14: Global Filter Configuration
 * Day 17: Added Telemetry Capture Filter
 * 
 * Centralized configuration for all global filters.
 * Refactored from individual filter classes for better organization.
 * 
 * Global filters are applied to ALL routes automatically.
 * They execute in order based on their Ordered.getOrder() value.
 * 
 * Filter execution order:
 * 1. LoggingFilter (HIGHEST_PRECEDENCE) - Logs all requests/responses
 * 2. TelemetryCaptureFilter (HIGHEST_PRECEDENCE + 1) - Captures metrics
 * 3. RateLimitResponseFilter (LOWEST_PRECEDENCE) - Adds rate limit headers
 */
@Slf4j
@Configuration
public class GlobalFilterConfiguration {

    /**
     * Day 3: Request/Response Logging Filter
     * 
     * Logs all incoming requests and outgoing responses for observability.
     * Executes first (HIGHEST_PRECEDENCE) to capture all traffic.
     * 
     * @return LoggingFilter bean
     */
    @Bean
    public LoggingFilter loggingFilter() {
        log.info("📝 Registering global LoggingFilter");
        return new LoggingFilter();
    }

    /**
     * Day 17: Telemetry Capture Filter
     * 
     * Captures request/response metrics and publishes to Kafka.
     * Executes early (HIGHEST_PRECEDENCE + 1) for accurate timing.
     * 
     * @param telemetryPublisher Publisher service for Kafka
     * @return TelemetryCaptureFilter bean
     */
    @Bean
    public TelemetryCaptureFilter telemetryCaptureFilter(TelemetryPublisher telemetryPublisher) {
        log.info("📊 Registering global TelemetryCaptureFilter");
        return new TelemetryCaptureFilter(telemetryPublisher);
    }

    /**
     * Day 13: Rate Limit Response Filter
     * 
     * Adds rate limit information to response headers.
     * Executes last (LOWEST_PRECEDENCE) after all other processing.
     * 
     * @return RateLimitResponseFilter bean
     */
    @Bean
    public RateLimitResponseFilter rateLimitResponseFilter() {
        log.info("🚦 Registering global RateLimitResponseFilter");
        return new RateLimitResponseFilter();
    }
}
