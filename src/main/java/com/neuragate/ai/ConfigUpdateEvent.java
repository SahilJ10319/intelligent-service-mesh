package com.neuragate.ai;

import java.time.Instant;

/**
 * Day 24: Configuration Update Event
 * 
 * Event record that logs whenever the AI changes a configuration.
 * Provides full audit trail for autonomous actions.
 * 
 * Fields:
 * - configKey: What configuration was changed
 * - oldValue: Previous value
 * - newValue: New value
 * - reason: Why the change was made (from AI diagnosis)
 * - timestamp: When the change occurred
 * - confidence: AI's confidence in this change
 * - triggeredBy: What triggered this change (AI, MANUAL, ANOMALY)
 * - severity: Severity level of the issue being addressed
 * 
 * This enables:
 * - Full audit trail of autonomous changes
 * - Rollback capability
 * - Compliance and governance
 * - Change impact analysis
 */
public record ConfigUpdateEvent(
        String configKey,
        String oldValue,
        String newValue,
        String reason,
        Instant timestamp,
        Integer confidence,
        TriggerSource triggeredBy,
        String severity) {
    /**
     * Source that triggered the configuration change.
     */
    public enum TriggerSource {
        AI_AUTONOMOUS, // AI made the change automatically
        AI_SUGGESTED, // AI suggested, human approved
        MANUAL, // Human-initiated change
        ANOMALY_DETECTED, // Triggered by anomaly detector
        SCHEDULED // Scheduled optimization
    }

    /**
     * Create event for AI-driven change.
     */
    public static ConfigUpdateEvent aiDriven(
            String configKey,
            String oldValue,
            String newValue,
            String reason,
            Integer confidence,
            String severity) {
        return new ConfigUpdateEvent(
                configKey,
                oldValue,
                newValue,
                reason,
                Instant.now(),
                confidence,
                TriggerSource.AI_AUTONOMOUS,
                severity);
    }

    /**
     * Create event for manual change.
     */
    public static ConfigUpdateEvent manual(
            String configKey,
            String oldValue,
            String newValue,
            String reason) {
        return new ConfigUpdateEvent(
                configKey,
                oldValue,
                newValue,
                reason,
                Instant.now(),
                100,
                TriggerSource.MANUAL,
                "N/A");
    }

    /**
     * Create event for anomaly-triggered change.
     */
    public static ConfigUpdateEvent anomalyTriggered(
            String configKey,
            String oldValue,
            String newValue,
            String reason,
            String severity) {
        return new ConfigUpdateEvent(
                configKey,
                oldValue,
                newValue,
                reason,
                Instant.now(),
                95,
                TriggerSource.ANOMALY_DETECTED,
                severity);
    }
}
