package com.neuragate.telemetry;

import com.neuragate.dashboard.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * WebFilter that captures telemetry for ALL HTTP requests
 * (not just Gateway-routed ones) and pushes events to the
 * live dashboard via SseEmitterService and MetricsBuffer.
 *
 * The original TelemetryCaptureFilter is a GlobalFilter which only
 * fires for Spring Cloud Gateway matched routes. This WebFilter
 * covers actuator, dashboard, auth, and all other controller endpoints
 * so the dashboard always shows live traffic.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class DashboardTelemetryFilter implements WebFilter {

    private final SseEmitterService sseEmitterService;
    private final MetricsBuffer metricsBuffer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startTime = System.currentTimeMillis();
        Instant timestamp = Instant.now();

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    try {
                        long latency = System.currentTimeMillis() - startTime;
                        String path = exchange.getRequest().getPath().value();
                        String method = exchange.getRequest().getMethod().name();
                        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                        int status = statusCode != null ? statusCode.value() : 0;

                        // Skip SSE stream endpoint to avoid feedback loop
                        if (path.contains("/dashboard/stream"))
                            return;

                        String correlationId = exchange.getRequest().getHeaders()
                                .getFirst("X-Correlation-ID");
                        String clientIp = exchange.getRequest().getRemoteAddress() != null
                                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                                : "unknown";

                        GatewayTelemetry telemetry = GatewayTelemetry.builder()
                                .path(path)
                                .method(method)
                                .status(status)
                                .latency(latency)
                                .timestamp(timestamp)
                                .correlationId(correlationId)
                                .clientIp(clientIp)
                                .rateLimited(status == 429)
                                .circuitBreakerTriggered(status == 503)
                                .retryCount(0)
                                .build();

                        // Push to metrics buffer for aggregation
                        metricsBuffer.addMetric(telemetry);

                        // Push to SSE for live dashboard
                        sseEmitterService.broadcastTelemetry(telemetry);

                        log.debug("Dashboard telemetry: {} {} -> {} ({}ms)",
                                method, path, status, latency);
                    } catch (Exception e) {
                        // Never let telemetry errors affect request processing
                        log.debug("Dashboard telemetry error: {}", e.getMessage());
                    }
                });
    }
}
