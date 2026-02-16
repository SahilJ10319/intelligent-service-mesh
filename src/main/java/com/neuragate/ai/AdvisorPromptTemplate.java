package com.neuragate.ai;

import org.springframework.stereotype.Component;

/**
 * Day 22: AI Advisor Prompt Template
 * 
 * Defines system prompts and templates for the AI advisor.
 * These prompts guide the AI to act as a Senior SRE analyzing gateway metrics.
 * 
 * Prompt engineering principles:
 * - Clear role definition (Senior SRE)
 * - Specific task (analyze metrics, identify bottlenecks)
 * - Structured output format (JSON)
 * - Context-aware (gateway-specific terminology)
 * - Actionable (suggest specific configuration changes)
 */
@Component
public class AdvisorPromptTemplate {

    /**
     * System prompt that defines the AI's role and behavior.
     * 
     * This prompt is sent once at the start of the conversation.
     */
    public String getSystemPrompt() {
        return """
                You are a Senior Site Reliability Engineer (SRE) specializing in API Gateway optimization and performance tuning.

                Your expertise includes:
                - Identifying performance bottlenecks from latency patterns
                - Diagnosing error rate spikes and their root causes
                - Recommending specific configuration changes for Spring Cloud Gateway
                - Tuning Resilience4j circuit breakers, retry policies, and rate limiters
                - Analyzing distributed system behavior and cascading failures

                Your analysis should be:
                - Data-driven: Base recommendations on the provided metrics
                - Specific: Suggest exact configuration values, not general advice
                - Actionable: Provide steps that can be implemented immediately
                - Risk-aware: Consider the impact of changes on system stability

                Always respond in JSON format with these fields:
                - diagnosis: What you identified (be specific)
                - severity: LOW, MEDIUM, HIGH, CRITICAL, or NORMAL
                - suggestedAction: Exact configuration change or remediation step
                - confidence: Your confidence level (0-100)
                - affectedRoutes: Which routes are impacted
                - metrics: Key metrics that support your diagnosis
                """;
    }

    /**
     * Build analysis prompt from metrics summary.
     * 
     * This prompt includes the actual metrics data for analysis.
     * 
     * @param metricsSummary Aggregated metrics from MetricsBuffer
     * @return Formatted prompt for AI analysis
     */
    public String buildAnalysisPrompt(String metricsSummary) {
        return String.format("""
                Analyze the following API Gateway metrics and provide your expert assessment:

                %s

                Based on these metrics:
                1. Identify any performance bottlenecks or anomalies
                2. Determine the severity of any issues found
                3. Suggest specific configuration changes to address the issues
                4. Estimate your confidence in this analysis

                Consider:
                - Is the average latency acceptable? (baseline: <200ms)
                - Is the error rate within normal bounds? (threshold: <5%%)
                - Are circuit breakers activating frequently? (indicates downstream issues)
                - Are rate limits being hit? (may need capacity increase)
                - Are there any patterns suggesting cascading failures?

                Respond ONLY with valid JSON matching this structure:
                {
                  "diagnosis": "string",
                  "severity": "LOW|MEDIUM|HIGH|CRITICAL|NORMAL",
                  "suggestedAction": "string",
                  "confidence": number,
                  "affectedRoutes": "string",
                  "metrics": "string"
                }
                """, metricsSummary);
    }

    /**
     * Build emergency analysis prompt for critical situations.
     * 
     * Used when anomaly detector triggers critical alerts.
     * 
     * @param anomalyDescription Description of the anomaly
     * @param metricsSummary     Current metrics
     * @return Urgent analysis prompt
     */
    public String buildEmergencyPrompt(String anomalyDescription, String metricsSummary) {
        return String.format("""
                🚨 CRITICAL ALERT - IMMEDIATE ANALYSIS REQUIRED

                Anomaly Detected: %s

                Current Metrics:
                %s

                This is an emergency situation. Provide:
                1. Immediate diagnosis of the root cause
                2. Urgent remediation steps (prioritize system stability)
                3. Risk assessment of suggested actions

                Respond with JSON including a "urgentActions" field with step-by-step remediation.
                """, anomalyDescription, metricsSummary);
    }

    /**
     * Build trend analysis prompt for proactive optimization.
     * 
     * Used for periodic analysis to identify optimization opportunities.
     * 
     * @param historicalSummary Historical metrics over time
     * @return Trend analysis prompt
     */
    public String buildTrendAnalysisPrompt(String historicalSummary) {
        return String.format("""
                Perform a trend analysis on the following historical gateway metrics:

                %s

                Identify:
                1. Performance trends (improving, degrading, stable)
                2. Capacity planning recommendations
                3. Proactive optimizations to prevent future issues
                4. Configuration tuning opportunities

                Focus on long-term system health and optimization, not immediate issues.
                """, historicalSummary);
    }
}
