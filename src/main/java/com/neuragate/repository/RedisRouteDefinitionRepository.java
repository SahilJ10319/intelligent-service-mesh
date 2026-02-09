package com.neuragate.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

// Redis-backed repository for dynamic route definitions
// Implements anti-fragile pattern: falls back to in-memory routes if Redis is down
// Day 9: Automatically injects circuit breaker filters into all dynamic routes
@Repository
public class RedisRouteDefinitionRepository implements RouteDefinitionRepository {

    private static final Logger logger = LoggerFactory.getLogger(RedisRouteDefinitionRepository.class);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final List<RouteDefinition> fallbackRoutes;
    private final String routeKey;

    public RedisRouteDefinitionRepository(
            ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            List<RouteDefinition> fallbackRoutes,
            @Value("${metadata.route-key}") String routeKey) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.fallbackRoutes = fallbackRoutes;
        this.routeKey = routeKey;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return redisTemplate.opsForHash()
                .values(routeKey)
                .map(this::deserializeRoute)
                .map(this::injectCircuitBreakerFilter) // Day 9: Auto-inject circuit breaker
                .onErrorResume(error -> {
                    logger.warn("Redis unavailable, using fallback routes: {}", error.getMessage());
                    return Flux.fromIterable(fallbackRoutes);
                });
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(r -> {
            try {
                String json = objectMapper.writeValueAsString(r);
                return redisTemplate.opsForHash()
                        .put(routeKey, r.getId(), json)
                        .then();
            } catch (JsonProcessingException e) {
                return Mono.error(e);
            }
        });
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id -> redisTemplate.opsForHash()
                .remove(routeKey, id)
                .then());
    }

    private RouteDefinition deserializeRoute(Object json) {
        try {
            return objectMapper.readValue(json.toString(), RouteDefinition.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize route", e);
        }
    }

    /**
     * Day 9: Inject circuit breaker filter into dynamic routes.
     * Day 10: Also inject retry filter (Retry wraps CircuitBreaker)
     * Day 13: Also inject rate limiter filter (optional, based on metadata)
     * 
     * This ensures ALL routes from Redis are automatically protected by
     * retry, circuit breaker, and optionally rate limiting patterns.
     * 
     * Filter order matters:
     * 1. RequestRateLimiter (outermost) - prevents traffic spikes
     * 2. Retry (middle) - handles transient failures
     * 3. CircuitBreaker (inner) - prevents cascading failures
     * 
     * Why this order?
     * - Rate limiter first to reject excess traffic early
     * - Retry handles transient failures for allowed requests
     * - CB protects against retry storms and persistent failures
     * 
     * @param route The route definition from Redis
     * @return The route with filters injected
     */
    private RouteDefinition injectCircuitBreakerFilter(RouteDefinition route) {
        // Check if route already has resilience filters
        boolean hasCircuitBreaker = route.getFilters() != null &&
                route.getFilters().stream()
                        .anyMatch(f -> "CircuitBreaker".equals(f.getName()));

        boolean hasRetry = route.getFilters() != null &&
                route.getFilters().stream()
                        .anyMatch(f -> "Retry".equals(f.getName()));

        boolean hasRateLimiter = route.getFilters() != null &&
                route.getFilters().stream()
                        .anyMatch(f -> "RequestRateLimiter".equals(f.getName()));

        if (hasCircuitBreaker && hasRetry && hasRateLimiter) {
            logger.debug("Route '{}' already has all resilience filters, skipping injection", route.getId());
            return route;
        }

        // Add to existing filters or create new list
        List<FilterDefinition> filters = route.getFilters() != null
                ? new ArrayList<>(route.getFilters())
                : new ArrayList<>();

        // Add circuit breaker if not present
        if (!hasCircuitBreaker) {
            FilterDefinition circuitBreakerFilter = new FilterDefinition();
            circuitBreakerFilter.setName("CircuitBreaker");
            circuitBreakerFilter.addArg("name", "dynamicRoute");
            circuitBreakerFilter.addArg("fallbackUri", "forward:/fallback/message");
            filters.add(0, circuitBreakerFilter);
            logger.info("Injected circuit breaker filter into route '{}'", route.getId());
        }

        // Add retry if not present (Retry wraps CircuitBreaker)
        if (!hasRetry) {
            FilterDefinition retryFilter = new FilterDefinition();
            retryFilter.setName("Retry");
            retryFilter.addArg("retries", "3");
            retryFilter.addArg("statuses", "BAD_GATEWAY,SERVICE_UNAVAILABLE");
            retryFilter.addArg("methods", "GET,POST,PUT,DELETE");
            retryFilter.addArg("exceptions", "java.net.ConnectException,java.io.IOException");

            // Add retry as first filter (wraps circuit breaker)
            filters.add(0, retryFilter);
            logger.info("Injected retry filter into route '{}'", route.getId());
        }

        // Day 13: Add rate limiter if metadata indicates it should be enabled
        if (!hasRateLimiter && shouldEnableRateLimiting(route)) {
            FilterDefinition rateLimiterFilter = new FilterDefinition();
            rateLimiterFilter.setName("RequestRateLimiter");
            rateLimiterFilter.addArg("redis-rate-limiter.replenishRate", "10");
            rateLimiterFilter.addArg("redis-rate-limiter.burstCapacity", "20");
            rateLimiterFilter.addArg("key-resolver", "#{@ipKeyResolver}");

            // Add rate limiter as first filter (outermost)
            filters.add(0, rateLimiterFilter);
            logger.info("Injected rate limiter filter into route '{}'", route.getId());
        }

        route.setFilters(filters);
        return route;
    }

    /**
     * Day 13: Check if rate limiting should be enabled for this route.
     * 
     * Checks route metadata for 'rate-limit-enabled' flag.
     * Defaults to false if not specified.
     * 
     * @param route The route definition
     * @return true if rate limiting should be enabled
     */
    private boolean shouldEnableRateLimiting(RouteDefinition route) {
        if (route.getMetadata() == null) {
            return false;
        }

        Object rateLimitEnabled = route.getMetadata().get("rate-limit-enabled");
        if (rateLimitEnabled instanceof Boolean) {
            return (Boolean) rateLimitEnabled;
        }

        if (rateLimitEnabled instanceof String) {
            return Boolean.parseBoolean((String) rateLimitEnabled);
        }

        return false;
    }
}
