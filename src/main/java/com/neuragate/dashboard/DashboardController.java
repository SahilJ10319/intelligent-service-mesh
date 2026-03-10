package com.neuragate.dashboard;

import com.neuragate.ai.ActionExecutor;
import com.neuragate.ai.AiAdvisorService;
import com.neuragate.ai.AiAnalysisResponse;
import com.neuragate.ai.ConfigUpdateEvent;
import com.neuragate.telemetry.MetricsBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Day 25 (WebFlux): Dashboard Controller
 *
 * Serves live metrics and AI decision data for the monitoring dashboard.
 *
 * Endpoints:
 * - GET /dashboard/stream – reactive SSE stream (text/event-stream)
 * - GET /dashboard/metrics – Current metrics snapshot (JSON)
 * - GET /dashboard/ai-log – Recent AI decisions (JSON)
 * - GET /dashboard/status – Gateway summary (JSON)
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final MetricsBuffer metricsBuffer;
    private final AiAdvisorService aiAdvisorService;
    private final ActionExecutor actionExecutor;
    private final SseEmitterService sseEmitterService;

    // ── SSE stream ────────────────────────────────────────────────────────────

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream() {
        log.info("SSE client connected to /dashboard/stream");
        return sseEmitterService.stream();
    }

    // ── REST snapshots ────────────────────────────────────────────────────────

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return buildMetricsSnapshot();
    }

    @GetMapping("/ai-log")
    public Map<String, Object> aiLog() {
        List<ConfigUpdateEvent> events = actionExecutor.getRecentAuditLog(20);
        AiAnalysisResponse latest = aiAdvisorService.getAdvice();

        return Map.of(
                "latestAnalysis", latest,
                "auditLog", events,
                "totalChanges", actionExecutor.getAuditLog().size());
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "bufferSize", metricsBuffer.getSize(),
                "bufferUtilization", metricsBuffer.getUtilization(),
                "connectedClients", sseEmitterService.getConnectedClients(),
                "timestamp", java.time.Instant.now().toString());
    }

    @GetMapping("/feed")
    public List<Map<String, Object>> feed() {
        return metricsBuffer.getRecentMetrics().stream()
                .sorted((a, b) -> {
                    if (a.getTimestamp() == null || b.getTimestamp() == null)
                        return 0;
                    return b.getTimestamp().compareTo(a.getTimestamp());
                })
                .limit(50)
                .map(t -> Map.<String, Object>of(
                        "method", t.getMethod() != null ? t.getMethod() : "GET",
                        "status", t.getStatus() != null ? t.getStatus() : 0,
                        "path", t.getPath() != null ? t.getPath() : "/",
                        "latency", t.getLatency() != null ? t.getLatency() : 0,
                        "timestamp", t.getTimestamp() != null ? t.getTimestamp().toString() : ""))
                .collect(java.util.stream.Collectors.toList());
    }

    // ── Scheduled broadcast ───────────────────────────────────────────────────

    @Scheduled(fixedRate = 5000, initialDelay = 5000)
    public void pushMetricsSnapshot() {
        if (sseEmitterService.getConnectedClients() == 0)
            return;
        sseEmitterService.broadcastMetricsSnapshot(buildMetricsSnapshot());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildMetricsSnapshot() {
        return Map.of(
                "avgLatency", metricsBuffer.getAverageLatency(),
                "errorRate", metricsBuffer.getErrorRate(),
                "bufferSize", metricsBuffer.getSize(),
                "bufferUtilization", metricsBuffer.getUtilization(),
                "rateLimitedCount", metricsBuffer.getRateLimitedCount(),
                "circuitBreakerCount", metricsBuffer.getCircuitBreakerCount(),
                "count2xx", metricsBuffer.getCountByStatus(200),
                "count4xx", metricsBuffer.getCountByStatus(404) + metricsBuffer.getCountByStatus(429),
                "count5xx", metricsBuffer.getCountByStatus(500) + metricsBuffer.getCountByStatus(503),
                "timestamp", java.time.Instant.now().toString());
    }
}
