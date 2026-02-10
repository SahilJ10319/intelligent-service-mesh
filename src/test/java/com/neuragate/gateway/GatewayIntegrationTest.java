package com.neuragate.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

/**
 * Day 14: Gateway Integration Tests
 * 
 * End-to-end integration tests for the NeuraGate gateway.
 * Tests the complete request flow from gateway to mock service.
 * 
 * Test scenarios:
 * - Gateway routing to mock inventory service
 * - Rate limiting enforcement (429 Too Many Requests)
 * - Circuit breaker integration
 * - Health endpoint availability
 * - Admin API functionality
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        // Configure test client with longer timeout for integration tests
        webTestClient = webTestClient.mutate()
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Test 1: Verify gateway routes requests to inventory mock service.
     * 
     * This test validates:
     * - Gateway is running and accepting requests
     * - Route definition is correctly configured
     * - Request is forwarded to mock service on port 9001
     * - Response is returned successfully
     */
    @Test
    void testGatewayRoutesToInventoryService() {
        webTestClient.get()
                .uri("/inventory")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].id").exists()
                .jsonPath("$[0].name").exists()
                .jsonPath("$[0].price").exists();
    }

    /**
     * Test 2: Verify specific product retrieval through gateway.
     */
    @Test
    void testGetProductById() {
        webTestClient.get()
                .uri("/inventory/1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.name").isEqualTo("Laptop Pro 16")
                .jsonPath("$.price").isEqualTo(2499.99);
    }

    /**
     * Test 3: Verify rate limiter returns 429 when limit exceeded.
     * 
     * This test validates:
     * - Rate limiter is active and enforcing limits
     * - Burst capacity is respected
     * - 429 status code is returned when limit exceeded
     * - Rate limit headers are present in response
     * 
     * Note: This test assumes default rate limit of 10 req/sec with burst of 20.
     * We make 25 requests rapidly to exceed the limit.
     */
    @Test
    void testRateLimiterReturns429WhenLimitExceeded() {
        // Make requests up to burst capacity (20) + some extra
        int requestCount = 25;
        int rateLimitedCount = 0;

        for (int i = 0; i < requestCount; i++) {
            var result = webTestClient.get()
                    .uri("/inventory")
                    .exchange();

            // Check if we got rate limited
            if (result.expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                    .returnResult(String.class)
                    .getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                rateLimitedCount++;
            }
        }

        // At least some requests should have been rate limited
        assert rateLimitedCount > 0 : "Expected some requests to be rate limited, but none were";
    }

    /**
     * Test 4: Verify rate limit headers are present in response.
     */
    @Test
    void testRateLimitHeadersPresent() {
        webTestClient.get()
                .uri("/inventory")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-RateLimit-Remaining")
                .expectHeader().exists("X-RateLimit-Replenish-Rate")
                .expectHeader().exists("X-RateLimit-Burst-Capacity");
    }

    /**
     * Test 5: Verify health endpoint is accessible.
     */
    @Test
    void testHealthEndpoint() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    /**
     * Test 6: Verify admin API can create routes.
     */
    @Test
    void testAdminApiCreateRoute() {
        String routeJson = """
                {
                    "id": "test-route",
                    "uri": "http://localhost:9001",
                    "path": "/test/**",
                    "order": 1
                }
                """;

        webTestClient.post()
                .uri("/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(routeJson)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("test-route")
                .jsonPath("$.uri").isEqualTo("http://localhost:9001");
    }

    /**
     * Test 7: Verify admin API can list routes.
     */
    @Test
    void testAdminApiListRoutes() {
        webTestClient.get()
                .uri("/admin/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray();
    }

    /**
     * Test 8: Verify circuit breaker fallback on service failure.
     * 
     * This test uses the flaky endpoint to trigger failures
     * and verify circuit breaker opens and returns fallback.
     */
    @Test
    void testCircuitBreakerFallback() {
        // Make multiple failing requests to open circuit breaker
        for (int i = 0; i < 10; i++) {
            webTestClient.get()
                    .uri("/inventory/flaky?failureRate=100")
                    .exchange();
            // Don't assert status - just trigger failures
        }

        // After circuit opens, should get fallback response
        webTestClient.get()
                .uri("/inventory")
                .exchange()
                .expectStatus().is5xxServerError(); // Circuit breaker fallback
    }

    /**
     * Test 9: Verify mock service chaos controls.
     */
    @Test
    void testMockServiceChaosControls() {
        String chaosConfig = """
                {
                    "rate": 0
                }
                """;

        webTestClient.post()
                .uri("http://localhost:9001/mock/config/failure-rate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(chaosConfig)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("success");
    }

    /**
     * Test 10: Verify inventory categories endpoint.
     */
    @Test
    void testInventoryByCategory() {
        webTestClient.get()
                .uri("/inventory/category/Electronics")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].category").isEqualTo("Electronics");
    }
}
