package com.neuragate.dashboard;

import com.neuragate.ai.ActionExecutor;
import com.neuragate.ai.AiAdvisorService;
import com.neuragate.ai.AiAnalysisResponse;
import com.neuragate.ai.ConfigUpdateEvent;
import com.neuragate.telemetry.MetricsBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Day 25: Dashboard Controller
 *
 * Serves live metrics and AI decision data for the monitoring dashboard.
 *
 * Endpoints:
 * - GET /dashboard/stream – SSE stream for real-time updates
 * - GET /dashboard/metrics – Current metrics snapshot (JSON)
 * - GET /dashboard/ai-log – Recent AI decisions (JSON)
 * - GET /dashboard/status – Gateway health summary (JSON)
 *
 * The static HTML dashboard (index.html) is served by Spring Boot's
 * default static-resource handler from src/main/resources/static/.
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

    @GetMapping("/stream")
    public SseEmitter stream() {
        log.info("📡 New SSE client connected to /dashboard/stream");
        return sseEmitterService.register();
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

    // ── Scheduled broadcast ───────────────────────────────────────────────────

    /** Push a metrics snapshot to all SSE clients every 5 seconds. */
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
