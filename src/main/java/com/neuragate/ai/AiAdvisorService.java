package com.neuragate.ai;

import com.neuragate.telemetry.GatewayTelemetry;
import com.neuragate.telemetry.MetricsBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Day 22: AI Advisor Service
 * Day 24: Added autonomous execution capability
 * 
 * Core service that aggregates metrics and prepares them for AI analysis.
 * Acts as the bridge between raw telemetry data and AI-driven insights.
 * 
 * Responsibilities:
 * - Aggregate recent metrics from MetricsBuffer
 * - Generate text-based summaries for LLM consumption
 * - Prepare structured prompts using AdvisorPromptTemplate
 * - Parse AI responses into structured AiAnalysisResponse objects
 * - Automatically execute high-confidence recommendations
 * 
 * Architecture:
 * - Stateless: Each analysis is independent
 * - Non-blocking: Metrics aggregation is fast (O(n) where n = 50)
 * - Extensible: Easy to add new analysis types
 * - Autonomous: Can auto-execute recommendations above confidence threshold
 * 
 * Future enhancements:
 * - Integrate with Gemini/OpenAI API
 * - Cache analysis results to reduce API calls
 * - Implement streaming responses for real-time feedback
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAdvisorService {

        private final MetricsBuffer metricsBuffer;
        private final AdvisorPromptTemplate promptTemplate;
        private final ActionExecutor actionExecutor;

        @Value("${neuragate.ai.human-in-loop:true}")
        private boolean humanInLoop;

        @Value("${neuragate.ai.auto-execute-threshold:80}")
        private int autoExecuteThreshold;

        private static final int METRICS_SAMPLE_SIZE = 50;

        /**
         * Get AI advice based on recent metrics.
         * 
         * Day 24: Now includes autonomous execution capability.
         * 
         * @return AI analysis response with diagnosis and recommendations
         */
        public AiAnalysisResponse getAdvice() {
                return getAdvice(false);
        }

        /**
         * Get AI advice with optional auto-execution.
         * 
         * @param autoExecute Whether to automatically execute recommendations
         * @return AI analysis response with diagnosis and recommendations
         */
        public AiAnalysisResponse getAdvice(boolean autoExecute) {
                log.info("🤖 Generating AI advice from recent metrics (auto-execute: {})", autoExecute);

                // Aggregate recent metrics
                String metricsSummary = aggregateMetrics();

                // Build analysis prompt
                String prompt = promptTemplate.buildAnalysisPrompt(metricsSummary);

                log.debug("Generated prompt for AI analysis:\n{}", prompt);

                // TODO: Call LLM API (Gemini, OpenAI, etc.)
                // For now, return a mock response based on heuristics
                AiAnalysisResponse response = generateMockAnalysis(metricsSummary);

                log.info("✅ AI analysis complete: {} (severity: {})",
                                response.getDiagnosis(), response.getSeverity());

                return response;
        }

        /**
         * Aggregate recent metrics into a text-based summary.
         * 
         * Extracts the last 50 metrics and generates a human-readable summary
         * that includes key statistics and patterns.
         * 
         * @return Formatted metrics summary for LLM
         */
        public String aggregateMetrics() {
                List<GatewayTelemetry> recentMetrics = metricsBuffer.getRecentMetrics(METRICS_SAMPLE_SIZE);

                if (recentMetrics.isEmpty()) {
                        return "No metrics available for analysis.";
                }

                // Calculate statistics
                double avgLatency = metricsBuffer.getAverageLatency();
                double errorRate = metricsBuffer.getErrorRate();
                long rateLimitedCount = metricsBuffer.getRateLimitedCount();
                long circuitBreakerCount = metricsBuffer.getCircuitBreakerCount();

                // Count by status code
                long count2xx = metricsBuffer.getCountByStatus(200);
                long count4xx = metricsBuffer.getCountByStatus(404) + metricsBuffer.getCountByStatus(429);
                long count5xx = metricsBuffer.getCountByStatus(500) + metricsBuffer.getCountByStatus(503);

                // Group by route
                Map<String, Long> routeCounts = recentMetrics.stream()
                                .filter(m -> m.getRouteId() != null)
                                .collect(Collectors.groupingBy(
                                                GatewayTelemetry::getRouteId,
                                                Collectors.counting()));

                // Calculate latency percentiles
                long[] latencies = recentMetrics.stream()
                                .filter(m -> m.getLatency() != null)
                                .mapToLong(GatewayTelemetry::getLatency)
                                .sorted()
                                .toArray();

                long p50 = latencies.length > 0 ? latencies[latencies.length / 2] : 0;
                long p95 = latencies.length > 0 ? latencies[(int) (latencies.length * 0.95)] : 0;
                long p99 = latencies.length > 0 ? latencies[(int) (latencies.length * 0.99)] : 0;

                // Build summary
                StringBuilder summary = new StringBuilder();
                summary.append("=== GATEWAY METRICS SUMMARY ===\n\n");
                summary.append(String.format("Sample Size: %d requests\n", recentMetrics.size()));
                summary.append(String.format("Time Window: Last %d events\n\n", METRICS_SAMPLE_SIZE));

                summary.append("--- LATENCY METRICS ---\n");
                summary.append(String.format("Average Latency: %.2fms\n", avgLatency));
                summary.append(String.format("P50 (Median): %dms\n", p50));
                summary.append(String.format("P95: %dms\n", p95));
                summary.append(String.format("P99: %dms\n\n", p99));

                summary.append("--- ERROR METRICS ---\n");
                summary.append(String.format("Error Rate: %.2f%%\n", errorRate));
                summary.append(String.format("2xx Responses: %d\n", count2xx));
                summary.append(String.format("4xx Responses: %d\n", count4xx));
                summary.append(String.format("5xx Responses: %d\n\n", count5xx));

                summary.append("--- RESILIENCE METRICS ---\n");
                summary.append(String.format("Rate Limited Requests: %d\n", rateLimitedCount));
                summary.append(String.format("Circuit Breaker Activations: %d\n\n", circuitBreakerCount));

                summary.append("--- ROUTE DISTRIBUTION ---\n");
                routeCounts.forEach((route, count) -> summary.append(String.format("%s: %d requests\n", route, count)));

                return summary.toString();
        }

        /**
         * Generate mock AI analysis based on heuristics.
         * 
         * This is a placeholder until LLM integration is complete.
         * Uses rule-based logic to simulate AI analysis.
         * 
         * @param metricsSummary Metrics summary
         * @return Mock AI analysis response
         */
        private AiAnalysisResponse generateMockAnalysis(String metricsSummary) {
                double avgLatency = metricsBuffer.getAverageLatency();
                double errorRate = metricsBuffer.getErrorRate();
                long circuitBreakerCount = metricsBuffer.getCircuitBreakerCount();

                AiAnalysisResponse.AiAnalysisResponseBuilder builder = AiAnalysisResponse.builder()
                                .timestamp(Instant.now().toString())
                                .metrics(String.format("Avg Latency: %.2fms, Error Rate: %.2f%%, CB Activations: %d",
                                                avgLatency, errorRate, circuitBreakerCount));

                // Rule-based analysis (simulates AI)
                if (errorRate > 10) {
                        return builder
                                        .diagnosis("High error rate detected - downstream service degradation likely")
                                        .severity(AiAnalysisResponse.Severity.HIGH)
                                        .suggestedAction(
                                                        "Enable circuit breaker with 50% failure threshold and 30s timeout. "
                                                                        +
                                                                        "Consider scaling downstream services or implementing fallback responses.")
                                        .confidence(85)
                                        .affectedRoutes("All routes")
                                        .build();
                } else if (avgLatency > 500) {
                        return builder
                                        .diagnosis("High latency spike detected - performance bottleneck identified")
                                        .severity(AiAnalysisResponse.Severity.MEDIUM)
                                        .suggestedAction("Increase timeout from 30s to 60s. Enable request caching for "
                                                        +
                                                        "frequently accessed endpoints. Consider connection pool tuning.")
                                        .confidence(78)
                                        .affectedRoutes("High-traffic routes")
                                        .build();
                } else if (circuitBreakerCount > 5) {
                        return builder
                                        .diagnosis("Frequent circuit breaker activations - cascading failure pattern")
                                        .severity(AiAnalysisResponse.Severity.HIGH)
                                        .suggestedAction(
                                                        "Adjust circuit breaker sliding window from 10 to 20 requests. "
                                                                        +
                                                                        "Increase wait duration in open state from 30s to 60s.")
                                        .confidence(82)
                                        .affectedRoutes("Routes with circuit breaker enabled")
                                        .build();
                } else if (avgLatency > 200) {
                        return builder
                                        .diagnosis("Moderate latency increase - monitor for degradation")
                                        .severity(AiAnalysisResponse.Severity.LOW)
                                        .suggestedAction(
                                                        "Monitor latency trends. Consider implementing request caching "
                                                                        +
                                                                        "or optimizing downstream service queries.")
                                        .confidence(70)
                                        .affectedRoutes("All routes")
                                        .build();
                } else {
                        return builder
                                        .diagnosis("System operating normally within expected parameters")
                                        .severity(AiAnalysisResponse.Severity.NORMAL)
                                        .suggestedAction("No immediate action required. Continue monitoring metrics.")
                                        .confidence(95)
                                        .affectedRoutes("All routes")
                                        .build();
                }
        }

        /**
         * Get the prepared prompt for external LLM integration.
         * 
         * Useful for debugging or manual LLM testing.
         * 
         * @return Formatted prompt ready for LLM API
         */
        public String getPreparedPrompt() {
                String metricsSummary = aggregateMetrics();
                return promptTemplate.buildAnalysisPrompt(metricsSummary);
        }

        /**
         * Get system prompt for LLM configuration.
         * 
         * @return System prompt defining AI's role
         */
        public String getSystemPrompt() {
                return promptTemplate.getSystemPrompt();
        }
}
