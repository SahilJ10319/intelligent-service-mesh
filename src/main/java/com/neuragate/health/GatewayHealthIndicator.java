package com.neuragate.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Day 6: Custom reactive health indicator for gateway self-awareness.
 * 
 * This indicator checks:
 * 1. Redis connectivity (critical for dynamic routing)
 * 2. Gateway's internal state
 * 
 * The gateway uses this to determine whether to use Redis routes or
 * fall back to emergency in-memory routes.
 * 
 * Why reactive?
 * - Non-blocking health checks preserve Virtual Thread benefits
 * - Can handle thousands of concurrent health check requests
 * - Integrates seamlessly with Spring Cloud Gateway's reactive stack
 */
@Component("gateway")
public class GatewayHealthIndicator implements ReactiveHealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(GatewayHealthIndicator.class);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(2);

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public GatewayHealthIndicator(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Health> health() {
        return checkRedisHealth()
                .map(redisHealthy -> {
                    if (redisHealthy) {
                        logger.debug("Gateway health check: UP - Redis connected");
                        return Health.up()
                                .withDetail("redis", "connected")
                                .withDetail("routing", "dynamic (Redis-backed)")
                                .withDetail("fallback", "available")
                                .build();
                    } else {
                        logger.warn("Gateway health check: DEGRADED - Redis unavailable, using fallback routes");
                        return Health.status("DEGRADED")
                                .withDetail("redis", "disconnected")
                                .withDetail("routing", "fallback (in-memory emergency routes)")
                                .withDetail("fallback", "active")
                                .withDetail("warning", "Dynamic routing unavailable")
                                .build();
                    }
                })
                .onErrorResume(error -> {
                    logger.error("Gateway health check failed", error);
                    return Mono.just(Health.down()
                            .withDetail("error", error.getMessage())
                            .withDetail("redis", "error")
                            .withDetail("routing", "unknown")
                            .build());
                });
    }

    /**
     * Check Redis connectivity using PING command.
     * 
     * Returns true if Redis responds with PONG within timeout,
     * false otherwise (including timeout).
     */
    private Mono<Boolean> checkRedisHealth() {
        return redisTemplate.execute(connection -> connection.ping())
                .next()
                .map(response -> "PONG".equals(response))
                .timeout(HEALTH_CHECK_TIMEOUT)
                .onErrorReturn(false) // Treat timeout or error as unhealthy
                .doOnNext(healthy -> {
                    if (!healthy) {
                        logger.warn("Redis health check failed or timed out");
                    }
                });
    }
}
