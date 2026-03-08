package com.neuragate.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuragate.telemetry.GatewayTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Day 25 (WebFlux fix): Server-Sent Events Service using reactive Sinks.
 *
 * Replaces the MVC SseEmitter with a WebFlux-compatible Sinks.Many multicast
 * bus. DashboardController exposes the Flux as a text/event-stream endpoint.
 * Every browser tab subscribed to /dashboard/stream gets each broadcast.
 */
@Slf4j
@Service
public class SseEmitterService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Multicast sink — all subscribers receive all events.
     * onBackpressureBuffer keeps events in a queue if a slow subscriber
     * falls behind (bounded at 256 messages).
     */
    private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer(256, false);

    private final AtomicInteger subscriberCount = new AtomicInteger(0);

    // ── Public broadcast API ───────────────────────────────────────────────────

    /** Called by TelemetryConsumer for every Kafka event. */
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
    public Flux<String> stream() {
        return sink.asFlux()
                .doOnSubscribe(s -> {
                    subscriberCount.incrementAndGet();
                    log.debug("SSE client connected (total: {})", subscriberCount.get());
                })
                .doOnCancel(() -> {
                    subscriberCount.decrementAndGet();
                    log.debug("SSE client disconnected (total: {})", subscriberCount.get());
                });
    }

    public int getConnectedClients() {
        return subscriberCount.get();
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private void emit(String eventName, Object data) {
        try {
            // SSE format: "event: <name>\ndata: <json>\n\n"
            String json = objectMapper.writeValueAsString(data);
            String message = "event: " + eventName + "\ndata: " + json + "\n\n";
            sink.tryEmitNext(message);
        } catch (Exception e) {
            log.debug("SSE emit error: {}", e.getMessage());
        }
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
