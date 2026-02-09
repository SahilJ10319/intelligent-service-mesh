package com.neuragate.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Day 13: Rate Limit Response Filter
 * 
 * Adds rate limit information to response headers.
 * This allows clients to see their current rate limit status
 * and adjust their request patterns accordingly.
 * 
 * Headers added:
 * - X-RateLimit-Remaining: Tokens remaining in bucket
 * - X-RateLimit-Replenish-Rate: Tokens added per second
 * - X-RateLimit-Burst-Capacity: Maximum tokens in bucket
 * 
 * These headers are set by the RedisRateLimiter and we just
 * ensure they're visible to the client.
 */
@Slf4j
@Component
public class RateLimitResponseFilter implements GlobalFilter, Ordered {

    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_REPLENISH_RATE_HEADER = "X-RateLimit-Replenish-Rate";
    private static final String RATE_LIMIT_BURST_CAPACITY_HEADER = "X-RateLimit-Burst-Capacity";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();

            // Check if rate limit headers are present
            String remaining = headers.getFirst(RATE_LIMIT_REMAINING_HEADER);

            if (remaining != null) {
                // Log rate limit status
                String replenishRate = headers.getFirst(RATE_LIMIT_REPLENISH_RATE_HEADER);
                String burstCapacity = headers.getFirst(RATE_LIMIT_BURST_CAPACITY_HEADER);

                log.debug("🚦 Rate Limit Status: Remaining={}, ReplenishRate={}, BurstCapacity={}",
                        remaining, replenishRate, burstCapacity);

                // Warn if getting close to limit
                try {
                    int remainingTokens = Integer.parseInt(remaining);
                    if (remainingTokens < 5) {
                        log.warn("⚠️  Client approaching rate limit: {} tokens remaining", remainingTokens);
                    }
                } catch (NumberFormatException e) {
                    log.debug("Could not parse remaining tokens: {}", remaining);
                }
            }
        }));
    }

    /**
     * Execute after rate limiting but before response is sent.
     * 
     * @return Order value (higher = later execution)
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
