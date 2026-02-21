package com.neuragate.stresstesting;

import com.neuragate.telemetry.MetricsBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Day 28: Load Test Service
 *
 * Generates configurable bursts of reactive HTTP traffic aimed at the gateway's
 * own chaos/mock endpoints to exercise circuit breakers, rate limiters, and
 * the full telemetry pipeline.
 *
 * Design:
 * - Reactive Flux ticker drives the request loop (no blocking threads)
 * - Project Loom virtual threads handle per-request execution
 * - Targets chaos-enabled paths: /inventory/*, /actuator/health
 * - Publishes live progress via a Sinks.Many (consumed by StressTestController)
 * - Rate: configurable via neuragate.stress.requests-per-minute (default 1000)
 *
 * Safety:
 * - AtomicBoolean guard prevents concurrent test runs
 * - Auto-stops after configured duration (default 60 s)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoadTestService {

    private final MetricsBuffer metricsBuffer;

    @Value("${neuragate.stress.requests-per-minute:1000}")
    private int requestsPerMinute;

    @Value("${neuragate.stress.duration-seconds:60}")
    private int durationSeconds;

    @Value("${server.port:8080}")
    private int serverPort;

    // ── State ─────────────────────────────────────────────────────────────────

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalSent = new AtomicLong(0);
    private final AtomicLong totalOk = new AtomicLong(0);
    private final AtomicLong totalFail = new AtomicLong(0);

    /** Live event bus for SSE-style progress (StressTestController subscribes). */
    private final Sinks.Many<String> eventBus = Sinks.many().multicast().onBackpressureBuffer();
    private final AtomicReference<Instant> startedAt = new AtomicReference<>();

    // ── Chaos target paths ────────────────────────────────────────────────────

    private static final String[] CHAOS_PATHS = {
            "/inventory/items",
            "/inventory/product/123",
            "/inventory/product/999",
            "/actuator/health",
            "/inventory/items?delay=true",
            "/inventory/items?error=true",
    };

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start the load test.
     *
     * @return summary map describing the test configuration on start, or error if
     *         already running.
     */
    public Map<String, Object> start() {
        if (!running.compareAndSet(false, true)) {
            return Map.of("status", "ALREADY_RUNNING",
                    "sent", totalSent.get(),
                    "ok", totalOk.get(),
                    "fail", totalFail.get());
        }

        totalSent.set(0);
        totalOk.set(0);
        totalFail.set(0);
        startedAt.set(Instant.now());

        long intervalMs = 60_000L / requestsPerMinute; // ms between requests
        log.info("🔥 Stress test STARTED — {} req/min, interval={}ms, duration={}s",
                requestsPerMinute, intervalMs, durationSeconds);

        emit("START | rate=%d req/min | duration=%ds".formatted(requestsPerMinute, durationSeconds));

        Flux.interval(Duration.ofMillis(intervalMs), Schedulers.boundedElastic())
                .take(requestsPerMinute * durationSeconds / 60) // total requests
                .takeWhile(tick -> running.get())
                .flatMap(tick -> sendRequest(), 50) // max 50 concurrent
                .doOnComplete(this::onComplete)
                .doOnError(e -> log.error("Load test error: {}", e.getMessage()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        return Map.of(
                "status", "STARTED",
                "requestsPerMinute", requestsPerMinute,
                "durationSeconds", durationSeconds,
                "targetPaths", CHAOS_PATHS,
                "startedAt", startedAt.get().toString());
    }

    /** Stop the load test early. */
    public Map<String, Object> stop() {
        running.set(false);
        log.info("🛑 Stress test manually STOPPED — sent={}, ok={}, fail={}",
                totalSent.get(), totalOk.get(), totalFail.get());
        emit("STOP | sent=%d | ok=%d | fail=%d".formatted(totalSent.get(), totalOk.get(), totalFail.get()));
        return status();
    }

    /** Current status snapshot. */
    public Map<String, Object> status() {
        Duration elapsed = startedAt.get() != null
                ? Duration.between(startedAt.get(), Instant.now())
                : Duration.ZERO;

        return Map.of(
                "running", running.get(),
                "totalSent", totalSent.get(),
                "totalOk", totalOk.get(),
                "totalFail", totalFail.get(),
                "elapsedSeconds", elapsed.toSeconds(),
                "errorRate", totalSent.get() == 0 ? 0.0
                        : (totalFail.get() * 100.0 / totalSent.get()),
                "metricsBufferSize", metricsBuffer.getSize());
    }

    /** Subscribe to live progress events (for SSE). */
    public Flux<String> events() {
        return eventBus.asFlux();
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private Mono<Void> sendRequest() {
        String path = CHAOS_PATHS[(int) (totalSent.get() % CHAOS_PATHS.length)];
        String url = "http://localhost:" + serverPort + path;
        totalSent.incrementAndGet();

        return Mono.fromCallable(() -> {
            try {
                var conn = new java.net.URI(url).toURL().openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                int code = ((java.net.HttpURLConnection) conn).getResponseCode();
                if (code < 500) {
                    totalOk.incrementAndGet();
                } else {
                    totalFail.incrementAndGet();
                }
                // Emit progress every 100 requests
                if (totalSent.get() % 100 == 0) {
                    emit("PROGRESS | sent=%d | ok=%d | fail=%d | path=%s"
                            .formatted(totalSent.get(), totalOk.get(), totalFail.get(), path));
                }
            } catch (Exception e) {
                totalFail.incrementAndGet();
                log.debug("Request to {} failed: {}", url, e.getMessage());
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private void onComplete() {
        running.set(false);
        log.info("✅ Stress test COMPLETE — sent={}, ok={}, fail={}, errorRate={}%",
                totalSent.get(), totalOk.get(), totalFail.get(),
                totalSent.get() == 0 ? 0 : (totalFail.get() * 100 / totalSent.get()));
        emit("DONE | sent=%d | ok=%d | fail=%d | errorRate=%.1f%%"
                .formatted(totalSent.get(), totalOk.get(), totalFail.get(),
                        totalSent.get() == 0 ? 0.0 : (totalFail.get() * 100.0 / totalSent.get())));
    }

    private void emit(String msg) {
        eventBus.tryEmitNext("[%s] %s".formatted(Instant.now(), msg));
    }
}
