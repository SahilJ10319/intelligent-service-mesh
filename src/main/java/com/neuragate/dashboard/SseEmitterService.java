package com.neuragate.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuragate.telemetry.GatewayTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Day 25: Server-Sent Events (SSE) Service
 *
 * Pushes live telemetry updates from the Kafka consumer directly to the
 * dashboard without a page refresh. Each browser tab that opens the
 * dashboard registers an SseEmitter here; when a new telemetry event
 * arrives the TelemetryConsumer calls broadcastTelemetry() and every
 * connected client receives the update instantly.
 *
 * Design:
 * - CopyOnWriteArrayList: safe for concurrent add/remove of emitters
 * - Timeout: 5 minutes per emitter (browser will reconnect automatically)
 * - Dead emitters are pruned on every broadcast
 */
@Slf4j
@Service
public class SseEmitterService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Register a new browser client. */
    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(300_000L); // 5-minute timeout

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError(e -> emitters.remove(emitter));

        emitters.add(emitter);
        log.debug("📡 SSE client registered (total: {})", emitters.size());
        return emitter;
    }

    /** Broadcast a telemetry event to all connected clients. */
    public void broadcastTelemetry(GatewayTelemetry telemetry) {
        if (emitters.isEmpty())
            return;

        Map<String, Object> payload = Map.of(
                "type", "telemetry",
                "path", telemetry.getPath() != null ? telemetry.getPath() : "",
                "method", telemetry.getMethod() != null ? telemetry.getMethod() : "",
                "status", telemetry.getStatus() != null ? telemetry.getStatus() : 0,
                "latency", telemetry.getLatency() != null ? telemetry.getLatency() : 0,
                "correlationId", telemetry.getCorrelationId() != null ? telemetry.getCorrelationId() : "",
                "timestamp", telemetry.getTimestamp() != null ? telemetry.getTimestamp().toString() : "");

        broadcast("telemetry", payload);
    }

    /** Broadcast an AI decision event to all connected clients. */
    public void broadcastAiDecision(String diagnosis, String severity, String action, int confidence) {
        if (emitters.isEmpty())
            return;

        Map<String, Object> payload = Map.of(
                "type", "ai_decision",
                "diagnosis", diagnosis,
                "severity", severity,
                "action", action,
                "confidence", confidence,
                "timestamp", java.time.Instant.now().toString());

        broadcast("ai_decision", payload);
    }

    /**
     * Broadcast a metrics snapshot (called every 5 seconds by DashboardController).
     */
    public void broadcastMetricsSnapshot(Map<String, Object> snapshot) {
        if (emitters.isEmpty())
            return;
        broadcast("metrics_snapshot", snapshot);
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private void broadcast(String eventName, Object data) {
        List<SseEmitter> dead = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                String json = objectMapper.writeValueAsString(data);
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (IOException e) {
                dead.add(emitter);
                log.debug("SSE emitter dead, removing: {}", e.getMessage());
            }
        }

        emitters.removeAll(dead);
    }

    public int getConnectedClients() {
        return emitters.size();
    }
}
