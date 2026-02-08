package com.neuragate.mock;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Day 12: Mock Configuration Controller
 * 
 * Provides endpoints to dynamically control chaos engineering settings
 * for the inventory mock service.
 * 
 * This allows testing of:
 * - Circuit breaker behavior under high failure rates
 * - Retry patterns with artificial latency
 * - Gateway resilience under various failure scenarios
 * 
 * Endpoints run on port 9001 alongside the inventory service.
 */
@Slf4j
@RestController
@RequestMapping("/mock/config")
@RequiredArgsConstructor
public class MockConfigController {

    private final ChaosSettings chaosSettings;

    /**
     * Set the failure rate for chaos testing.
     * 
     * @param request Failure rate configuration
     * @return Updated chaos settings
     */
    @PostMapping("/failure-rate")
    public Mono<ChaosResponse> setFailureRate(@RequestBody FailureRateRequest request) {
        log.warn("⚠️  Setting failure rate to {}%", request.getRate());

        try {
            chaosSettings.setFailureRate(request.getRate());

            return Mono.just(new ChaosResponse(
                    "success",
                    String.format("Failure rate set to %d%%", request.getRate()),
                    chaosSettings.getFailureRate(),
                    chaosSettings.getLatencyMs()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid failure rate: {}", e.getMessage());
            return Mono.just(new ChaosResponse(
                    "error",
                    e.getMessage(),
                    chaosSettings.getFailureRate(),
                    chaosSettings.getLatencyMs()));
        }
    }

    /**
     * Set the artificial latency for chaos testing.
     * 
     * @param request Latency configuration
     * @return Updated chaos settings
     */
    @PostMapping("/latency")
    public Mono<ChaosResponse> setLatency(@RequestBody LatencyRequest request) {
        log.warn("⚠️  Setting latency to {}ms", request.getLatencyMs());

        try {
            chaosSettings.setLatencyMs(request.getLatencyMs());

            return Mono.just(new ChaosResponse(
                    "success",
                    String.format("Latency set to %dms", request.getLatencyMs()),
                    chaosSettings.getFailureRate(),
                    chaosSettings.getLatencyMs()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid latency: {}", e.getMessage());
            return Mono.just(new ChaosResponse(
                    "error",
                    e.getMessage(),
                    chaosSettings.getFailureRate(),
                    chaosSettings.getLatencyMs()));
        }
    }

    /**
     * Get current chaos settings.
     * 
     * @return Current chaos configuration
     */
    @GetMapping("/status")
    public Mono<ChaosResponse> getStatus() {
        log.debug("Fetching chaos settings status");

        return Mono.just(new ChaosResponse(
                chaosSettings.isChaosActive() ? "chaos_active" : "healthy",
                chaosSettings.isChaosActive()
                        ? "Chaos mode is active"
                        : "Service is in healthy state",
                chaosSettings.getFailureRate(),
                chaosSettings.getLatencyMs()));
    }

    /**
     * Reset all chaos settings to healthy state.
     * 
     * @return Reset confirmation
     */
    @PostMapping("/reset")
    public Mono<ChaosResponse> reset() {
        log.info("🔄 Resetting chaos settings to healthy state");

        chaosSettings.reset();

        return Mono.just(new ChaosResponse(
                "success",
                "All chaos settings reset to healthy state",
                0,
                0));
    }

    /**
     * Set multiple chaos parameters at once.
     * 
     * @param request Combined chaos configuration
     * @return Updated chaos settings
     */
    @PostMapping("/batch")
    public Mono<ChaosResponse> setBatch(@RequestBody BatchChaosRequest request) {
        log.warn("⚠️  Batch update: Failure rate={}%, Latency={}ms",
                request.getFailureRate(), request.getLatencyMs());

        try {
            if (request.getFailureRate() != null) {
                chaosSettings.setFailureRate(request.getFailureRate());
            }
            if (request.getLatencyMs() != null) {
                chaosSettings.setLatencyMs(request.getLatencyMs());
            }

            return Mono.just(new ChaosResponse(
                    "success",
                    "Chaos settings updated",
                    chaosSettings.getFailureRate(),
                    chaosSettings.getLatencyMs()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid chaos settings: {}", e.getMessage());
            return Mono.just(new ChaosResponse(
                    "error",
                    e.getMessage(),
                    chaosSettings.getFailureRate(),
                    chaosSettings.getLatencyMs()));
        }
    }

    // DTOs
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureRateRequest {
        private Integer rate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatencyRequest {
        private Integer latencyMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchChaosRequest {
        private Integer failureRate;
        private Integer latencyMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChaosResponse {
        private String status;
        private String message;
        private Integer currentFailureRate;
        private Integer currentLatencyMs;
    }
}
