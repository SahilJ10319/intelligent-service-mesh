package com.neuragate.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Day 23: AI Analysis Response
 * 
 * DTO/Record that holds the AI's analysis output.
 * 
 * Fields:
 * - diagnosis: What the AI identified (e.g., "High latency spike detected")
 * - severity: Impact level (LOW, MEDIUM, HIGH, CRITICAL)
 * - suggestedAction: Specific configuration change or remediation step
 * - confidence: AI's confidence level (0-100)
 * - affectedRoutes: Routes impacted by the issue
 * - metrics: Key metrics that led to this diagnosis
 * 
 * This structured response enables:
 * - Automated alerting based on severity
 * - Action tracking and audit trail
 * - Confidence-based decision making
 * - Route-specific remediation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResponse {

    /**
     * AI's diagnosis of the current system state.
     * 
     * Examples:
     * - "High latency spike detected on /inventory endpoint"
     * - "Elevated error rate indicates downstream service degradation"
     * - "Circuit breaker activation pattern suggests cascading failures"
     * - "System operating normally within expected parameters"
     */
    private String diagnosis;

    /**
     * Severity level of the identified issue.
     * 
     * Levels:
     * - LOW: Minor performance degradation, no immediate action needed
     * - MEDIUM: Noticeable impact, should investigate soon
     * - HIGH: Significant impact, requires immediate attention
     * - CRITICAL: System stability at risk, urgent action required
     */
    private Severity severity;

    /**
     * Specific action recommended by the AI.
     * 
     * Examples:
     * - "Increase circuit breaker timeout from 30s to 60s"
     * - "Scale up inventory service to handle increased load"
     * - "Enable rate limiting on /api/inventory endpoint"
     * - "Investigate database connection pool exhaustion"
     */
    private String suggestedAction;

    /**
     * AI's confidence in this analysis (0-100).
     * 
     * Confidence levels:
     * - 90-100: High confidence, safe to auto-remediate
     * - 70-89: Medium confidence, human review recommended
     * - 50-69: Low confidence, requires investigation
     * - 0-49: Very low confidence, likely insufficient data
     */
    private Integer confidence;

    /**
     * Routes affected by the identified issue.
     * 
     * Enables route-specific remediation and alerting.
     */
    private String affectedRoutes;

    /**
     * Key metrics that led to this diagnosis.
     * 
     * Examples:
     * - "Avg latency: 450ms (baseline: 100ms)"
     * - "Error rate: 15% (threshold: 5%)"
     * - "Circuit breaker activations: 12 in last 5 minutes"
     */
    private String metrics;

    /**
     * Timestamp of the analysis.
     */
    private String timestamp;

    /**
     * Severity levels for AI analysis.
     */
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL,
        NORMAL
    }
}
