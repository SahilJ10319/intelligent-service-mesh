package com.neuragate.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Day 13: Rate Limiter Configuration
 * 
 * Implements distributed rate limiting using Redis token bucket algorithm.
 * 
 * Token Bucket Algorithm:
 * - Tokens are added to bucket at a constant rate (replenishRate)
 * - Bucket has maximum capacity (burstCapacity)
 * - Each request consumes 1 token
 * - Request is allowed if tokens available, rejected otherwise
 * 
 * Why Redis?
 * - Distributed rate limiting across multiple gateway instances
 * - Atomic operations for token consumption
 * - Persistence and high performance
 * - Shared state across all gateway nodes
 */
@Slf4j
@Configuration
public class RateLimiterConfig {

    /**
     * Default rate limiter for all routes.
     * 
     * Configuration:
     * - replenishRate: 10 tokens/second (sustainable rate)
     * - burstCapacity: 20 tokens (allows short bursts)
     * - requestedTokens: 1 token per request
     * 
     * This means:
     * - Normal traffic: 10 requests/second sustained
     * - Burst traffic: Up to 20 requests immediately, then 10/sec
     * 
     * @return RedisRateLimiter bean
     */
    @Bean
    public RedisRateLimiter defaultRateLimiter() {
        log.info("🚦 Configuring default rate limiter: 10 req/sec replenish, 20 burst capacity");
        return new RedisRateLimiter(10, 20, 1);
    }

    /**
     * Stricter rate limiter for critical/expensive endpoints.
     * 
     * Configuration:
     * - replenishRate: 5 tokens/second
     * - burstCapacity: 10 tokens
     * 
     * @return RedisRateLimiter bean for critical endpoints
     */
    @Bean
    public RedisRateLimiter criticalRateLimiter() {
        log.info("🚦 Configuring critical rate limiter: 5 req/sec replenish, 10 burst capacity");
        return new RedisRateLimiter(5, 10, 1);
    }

    /**
     * Lenient rate limiter for public/read-only endpoints.
     * 
     * Configuration:
     * - replenishRate: 50 tokens/second
     * - burstCapacity: 100 tokens
     * 
     * @return RedisRateLimiter bean for public endpoints
     */
    @Bean
    public RedisRateLimiter publicRateLimiter() {
        log.info("🚦 Configuring public rate limiter: 50 req/sec replenish, 100 burst capacity");
        return new RedisRateLimiter(50, 100, 1);
    }

    /**
     * IP-based key resolver.
     * 
     * Rate limits are applied per client IP address.
     * This prevents a single client from overwhelming the gateway.
     * 
     * @return KeyResolver that uses client IP
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest()
                    .getRemoteAddress()
                    .getAddress()
                    .getHostAddress();

            log.debug("🔑 Rate limit key resolved: IP={}", ip);
            return Mono.just(ip);
        };
    }

    /**
     * User ID-based key resolver.
     * 
     * Rate limits are applied per user (from X-User-Id header).
     * Falls back to IP if header not present.
     * 
     * Use this when you have authenticated users and want
     * per-user rate limiting instead of per-IP.
     * 
     * @return KeyResolver that uses user ID or IP
     */
    @Bean
    public KeyResolver userIdKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-User-Id");

            if (userId != null && !userId.isEmpty()) {
                log.debug("🔑 Rate limit key resolved: User-ID={}", userId);
                return Mono.just(userId);
            }

            // Fallback to IP if no user ID
            String ip = exchange.getRequest()
                    .getRemoteAddress()
                    .getAddress()
                    .getHostAddress();

            log.debug("🔑 Rate limit key resolved: IP={} (no User-ID header)", ip);
            return Mono.just(ip);
        };
    }

    /**
     * Path-based key resolver.
     * 
     * Rate limits are applied per endpoint path.
     * This protects specific endpoints from being overwhelmed.
     * 
     * @return KeyResolver that uses request path
     */
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> {
            String path = exchange.getRequest().getPath().value();
            log.debug("🔑 Rate limit key resolved: Path={}", path);
            return Mono.just(path);
        };
    }

    /**
     * Combined IP + Path key resolver.
     * 
     * Rate limits are applied per IP per endpoint.
     * Most granular control - prevents single IP from
     * overwhelming specific endpoints.
     * 
     * @return KeyResolver that combines IP and path
     */
    @Bean
    public KeyResolver combinedKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest()
                    .getRemoteAddress()
                    .getAddress()
                    .getHostAddress();

            String path = exchange.getRequest().getPath().value();
            String key = ip + ":" + path;

            log.debug("🔑 Rate limit key resolved: Combined={}", key);
            return Mono.just(key);
        };
    }
}
