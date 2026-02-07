package com.neuragate.mock;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Day 11: Mock Service Configuration
 * 
 * Configuration for the inventory mock service.
 * This runs as part of the same application but on a different port (9001)
 * to simulate a separate microservice.
 * 
 * Why mock services?
 * - Test gateway routing without external dependencies
 * - Simulate various failure scenarios
 * - Validate circuit breaker and retry behavior
 * - Demonstrate multi-service architecture
 */
@Slf4j
@Configuration
@EnableWebFlux
public class MockServiceConfig {

    @PostConstruct
    public void init() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  Mock Inventory Service Initialized                       ║");
        log.info("║  Port: 9001                                                ║");
        log.info("║  Endpoints:                                                ║");
        log.info("║    GET /api/inventory           - All products            ║");
        log.info("║    GET /api/inventory/{id}      - Product by ID           ║");
        log.info("║    GET /api/inventory/category/{category} - By category   ║");
        log.info("║    GET /api/inventory/{id}/stock - Stock status           ║");
        log.info("║    GET /api/inventory/slow      - Slow response (testing) ║");
        log.info("║    GET /api/inventory/flaky     - Random failures         ║");
        log.info("║    GET /api/inventory/health    - Health check            ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
    }
}
