package com.neuragate.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Day 18: Correlation ID Filter
 * 
 * Implements distributed tracing by ensuring every request has a unique
 * correlation ID.
 * This ID travels through the entire request chain, enabling end-to-end
 * tracing.
 * 
 * Flow:
 * 1. Check if request has X-Correlation-ID header
 * 2. If missing, generate new UUID
 * 3. Inject ID into request headers (for downstream services)
 * 4. Add ID to response headers (for client visibility)
 * 5. Log ID for debugging
 * 
 * Benefits:
 * - Trace requests across multiple services
 * - Correlate logs from different components
 * - Debug distributed transactions
 * - Track request flow through the mesh
 * 
 * Order: HIGHEST_PRECEDENCE
 * - Must run first to ensure ID is available for all other filters
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_ATTR = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Check if correlation ID already exists in request
        String correlationId = exchange.getRequest()
                .getHeaders()
                .getFirst(CORRELATION_ID_HEADER);

        // Generate new ID if missing
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("🆔 Generated new correlation ID: {}", correlationId);
        } else {
            log.debug("🆔 Using existing correlation ID: {}", correlationId);
        }

        // Store in exchange attributes for easy access by other filters
        exchange.getAttributes().put(CORRELATION_ID_ATTR, correlationId);

        // Inject correlation ID into request headers for downstream services
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        // Create mutated exchange with updated request
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Add correlation ID to response headers for client visibility
        mutatedExchange.getResponse()
                .getHeaders()
                .add(CORRELATION_ID_HEADER, correlationId);

        // Log request with correlation ID
        log.info("🔗 Request [{}] {} {} - Correlation ID: {}",
                correlationId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                correlationId);

        // Continue filter chain with mutated exchange
        return chain.filter(mutatedExchange);
    }

    /**
     * Execute first to ensure correlation ID is available for all filters.
     * 
     * @return HIGHEST_PRECEDENCE to run before all other filters
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Helper method to extract correlation ID from exchange attributes.
     * 
     * @param exchange ServerWebExchange
     * @return Correlation ID or null if not found
     */
    public static String getCorrelationId(ServerWebExchange exchange) {
        return exchange.getAttribute(CORRELATION_ID_ATTR);
    }
}
