package com.neuragate.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Day 23: AI Controller
 * 
 * REST API for AI advisor functionality.
 * Provides endpoints to trigger analysis and retrieve AI recommendations.
 * 
 * Endpoints:
 * - GET /ai/analyze: Trigger full analysis and get recommendations
 * - GET /ai/prompt: View the prepared prompt (for debugging)
 * - GET /ai/system-prompt: View the system prompt configuration
 * 
 * This gives the gateway its "Voice" - the ability to self-diagnose
 * and recommend configuration changes based on observed behavior.
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAdvisorService aiAdvisorService;

    /**
     * Trigger AI analysis of recent metrics.
     * 
     * This is the main endpoint that users/operators call to get
     * AI-driven recommendations for gateway optimization.
     * 
     * Example response:
     * {
     * "diagnosis": "High latency spike detected",
     * "severity": "MEDIUM",
     * "suggestedAction": "Increase timeout from 30s to 60s",
     * "confidence": 78,
     * "affectedRoutes": "High-traffic routes",
     * "metrics": "Avg Latency: 450ms, Error Rate: 2.5%"
     * }
     * 
     * @return AI analysis response with diagnosis and recommendations
     */
    @GetMapping("/analyze")
    public AiAnalysisResponse analyze() {
        log.info("🤖 AI analysis requested via /ai/analyze endpoint");

        AiAnalysisResponse response = aiAdvisorService.getAdvice();

        log.info("📊 Analysis complete: {} ({})",
                response.getDiagnosis(),
                response.getSeverity());

        return response;
    }

    /**
     * Get the prepared prompt for debugging.
     * 
     * Useful for:
     * - Testing prompt templates
     * - Manual LLM integration
     * - Debugging metric aggregation
     * 
     * @return Formatted prompt ready for LLM
     */
    @GetMapping("/prompt")
    public PromptResponse getPrompt() {
        log.info("📝 Prompt requested via /ai/prompt endpoint");

        String systemPrompt = aiAdvisorService.getSystemPrompt();
        String analysisPrompt = aiAdvisorService.getPreparedPrompt();

        return new PromptResponse(systemPrompt, analysisPrompt);
    }

    /**
     * Get the system prompt configuration.
     * 
     * Shows how the AI is configured to behave.
     * 
     * @return System prompt text
     */
    @GetMapping("/system-prompt")
    public SystemPromptResponse getSystemPrompt() {
        log.info("⚙️  System prompt requested via /ai/system-prompt endpoint");

        String systemPrompt = aiAdvisorService.getSystemPrompt();

        return new SystemPromptResponse(systemPrompt);
    }

    /**
     * Response DTO for prompt endpoint.
     */
    public record PromptResponse(
            String systemPrompt,
            String analysisPrompt) {
    }

    /**
     * Response DTO for system prompt endpoint.
     */
    public record SystemPromptResponse(
            String systemPrompt) {
    }
}
