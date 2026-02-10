package com.neuragate.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// Day 3: Global filter that logs every incoming request
// Day 14: Refactored - now registered in GlobalFilterConfiguration
// This is the foundation for the telemetry system we'll build later
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        logger.info("Incoming request: {} {}", method, path);

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1; // Run this filter first
    }
}
