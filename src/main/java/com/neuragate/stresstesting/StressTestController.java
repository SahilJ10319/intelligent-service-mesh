package com.neuragate.stresstesting;

import com.neuragate.mock.ChaosSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Day 28: Stress Test Controller
 *
 * Secured admin endpoint to start, stop, and monitor the in-process load
 * generator. All endpoints require ROLE_ADMIN (enforced at method level via
 * 
 * @PreAuthorize — belt-and-suspenders on top of SecurityConfig path rules).
 *
 *               Endpoints:
 *               - POST /admin/test/stress/start — launch the load test
 *               - POST /admin/test/stress/stop — stop early
 *               - GET /admin/test/stress/status — current counters
 *               - GET /admin/test/stress/events — SSE stream of live progress
 *               - POST /admin/test/chaos — configure chaos settings before test
 *               - POST /admin/test/chaos/reset — reset chaos to healthy state
 */
@Slf4j
@RestController
@RequestMapping("/admin/test")
@RequiredArgsConstructor
public class StressTestController {

    private final LoadTestService loadTestService;
    private final ChaosSettings chaosSettings;

    // ── Load Test ─────────────────────────────────────────────────────────────

    @PostMapping("/stress/start")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Map<String, Object>> startStressTest() {
        log.info("🔥 Admin triggered stress test start");
        return Mono.fromCallable(loadTestService::start);
    }

    @PostMapping("/stress/stop")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Map<String, Object>> stopStressTest() {
        log.info("🛑 Admin triggered stress test stop");
        return Mono.fromCallable(loadTestService::stop);
    }

    @GetMapping("/stress/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Map<String, Object>> stressTestStatus() {
        return Mono.fromCallable(loadTestService::status);
    }

    /**
     * SSE stream — browser or curl can subscribe for live progress.
     * Example: curl -N http://localhost:8080/admin/test/stress/events -H
     * "Authorization: Bearer <jwt>"
     */
    @GetMapping(value = "/stress/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<String> stressTestEvents() {
        return loadTestService.events();
    }

    // ── Chaos Control ─────────────────────────────────────────────────────────

    /**
     * Configure chaos before starting the stress test.
     * Body: { "failureRate": 30, "latencyMs": 200 }
     */
    @PostMapping("/chaos")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Map<String, Object>> configureChaos(@RequestBody Map<String, Integer> config) {
        Integer failureRate = config.get("failureRate");
        Integer latencyMs = config.get("latencyMs");

        if (failureRate != null) {
            chaosSettings.setFailureRate(failureRate);
            log.warn("⚠️  Chaos failure rate set to {}%", failureRate);
        }
        if (latencyMs != null) {
            chaosSettings.setLatencyMs(latencyMs);
            log.warn("⚠️  Chaos latency set to {}ms", latencyMs);
        }

        return Mono.just(Map.of(
                "status", "CHAOS_CONFIGURED",
                "failureRate", chaosSettings.getFailureRate(),
                "latencyMs", chaosSettings.getLatencyMs(),
                "chaosActive", chaosSettings.isChaosActive()));
    }

    @PostMapping("/chaos/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Map<String, Object>> resetChaos() {
        chaosSettings.reset();
        log.info("🔄 Chaos settings reset to healthy by admin");
        return Mono.just(Map.of(
                "status", "CHAOS_RESET",
                "message", "All chaos settings restored to healthy state"));
    }
}
