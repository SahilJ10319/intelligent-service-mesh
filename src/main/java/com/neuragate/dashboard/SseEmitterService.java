package com.neuragate.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuragate.telemetry.GatewayTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Day 25 (WebFlux fix): Server-Sent Events Service using reactive Sinks.
 *
 * Emits ServerSentEvent objects so Spring correctly renders the SSE
 * "event:" and "data:" fields. The browser's EventSource.addEventListener()
 * receives named events (telemetry, ai_decision, metrics_snapshot).
 */
@Slf4j
@Service
public class SseEmitterService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer(256, false);

    private final AtomicInteger subscriberCount = new AtomicInteger(0);

    // ── Public broadcast API ───────────────────────────────────────────────────

    /** Called by DashboardTelemetryFilter / TelemetryConsumer for every request. */
    public void broadcastTelemetry(GatewayTelemetry telemetry) {
        Map<String, Object> payload = Map.of(
                "type", "telemetry",
                "path", nullSafe(telemetry.getPath()),
                "method", nullSafe(telemetry.getMethod()),
                "status", telemetry.getStatus() != null ? telemetry.getStatus() : 0,
                "latency", telemetry.getLatency() != null ? telemetry.getLatency() : 0,
                "correlationId", nullSafe(telemetry.getCorrelationId()),
                "timestamp", telemetry.getTimestamp() != null
                        ? telemetry.getTimestamp().toString()
                        : Instant.now().toString());
        emit("telemetry", payload);
    }

    /** Called by AiAdvisorService when a decision is made. */
    public void broadcastAiDecision(String diagnosis, String severity, String action, int confidence) {
        Map<String, Object> payload = Map.of(
                "type", "ai_decision",
                "diagnosis", diagnosis,
                "severity", severity,
                "action", action,
                "confidence", confidence,
                "timestamp", Instant.now().toString());
        emit("ai_decision", payload);
    }

    /** Called by DashboardController every 5 s. */
    public void broadcastMetricsSnapshot(Map<String, Object> snapshot) {
        emit("metrics_snapshot", snapshot);
    }

    /** Returns the Flux that DashboardController exposes as SSE. */
    public Flux<ServerSentEvent<String>> stream() {
        return sink.asFlux()
                .doOnSubscribe(s -> {
                    subscriberCount.incrementAndGet();
                    log.info("SSE client connected (total: {})", subscriberCount.get());
                })
                .doOnCancel(() -> {
                    subscriberCount.decrementAndGet();
                    log.info("SSE client disconnected (total: {})", subscriberCount.get());
                });
    }

    public int getConnectedClients() {
        return subscriberCount.get();
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private void emit(String eventName, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            ServerSentEvent<String> sse = ServerSentEvent.<String>builder()
                    .event(eventName)
                    .data(json)
                    .build();
            sink.tryEmitNext(sse);
        } catch (Exception e) {
            log.debug("SSE emit error: {}", e.getMessage());
        }
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
