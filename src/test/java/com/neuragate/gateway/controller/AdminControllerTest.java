package com.neuragate.gateway.controller;

import com.neuragate.gateway.model.RouteRequest;
import com.neuragate.repository.RedisRouteDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Day 7: Unit tests for AdminController.
 * 
 * Tests the REST API using WebTestClient to verify:
 * - GET /admin/routes - List all routes
 * - POST /admin/routes - Create/update a route
 * - DELETE /admin/routes/{id} - Delete a route
 * 
 * Why WebTestClient?
 * - Designed for testing reactive web applications
 * - Non-blocking, works with reactive controllers
 * - Provides fluent API for assertions
 * - No need to start full server (uses mock environment)
 */
@WebFluxTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private RedisRouteDefinitionRepository routeRepository;

    private RouteDefinition testRoute;

    @BeforeEach
    void setUp() {
        testRoute = new RouteDefinition();
        testRoute.setId("test-route");
        testRoute.setUri(URI.create("http://test.example.com"));
    }

    @Test
    void testListRoutes_Success() {
        // Given: Routes exist in repository
        RouteDefinition route1 = new RouteDefinition();
        route1.setId("route-1");
        route1.setUri(URI.create("http://service1.example.com"));

        RouteDefinition route2 = new RouteDefinition();
        route2.setId("route-2");
        route2.setUri(URI.create("http://service2.example.com"));

        when(routeRepository.getRouteDefinitions())
                .thenReturn(Flux.just(route1, route2));

        // When/Then: GET /admin/routes returns all routes
        webTestClient.get()
                .uri("/admin/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(RouteDefinition.class)
                .hasSize(2)
                .contains(route1, route2);

        verify(routeRepository).getRouteDefinitions();
    }

    @Test
    void testListRoutes_Empty() {
        // Given: No routes exist
        when(routeRepository.getRouteDefinitions())
                .thenReturn(Flux.empty());

        // When/Then: GET /admin/routes returns empty list
        webTestClient.get()
                .uri("/admin/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(RouteDefinition.class)
                .hasSize(0);
    }

    @Test
    void testCreateRoute_Success() {
        // Given: A valid route request
        RouteRequest request = RouteRequest.builder()
                .id("new-route")
                .uri("http://newservice.example.com")
                .path("/api/new/**")
                .order(1)
                .enabled(true)
                .build();

        // When: Repository save succeeds
        when(routeRepository.save(any(Mono.class)))
                .thenReturn(Mono.empty());

        // Then: POST /admin/routes creates the route
        webTestClient.post()
                .uri("/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("new-route")
                .jsonPath("$.uri").isEqualTo("http://newservice.example.com");

        verify(routeRepository).save(any(Mono.class));
    }

    @Test
    void testCreateRoute_WithCircuitBreaker() {
        // Given: A route request with circuit breaker
        RouteRequest request = RouteRequest.builder()
                .id("cb-route")
                .uri("http://cbservice.example.com")
                .path("/api/cb/**")
                .circuitBreakerName("testCircuitBreaker")
                .order(1)
                .build();

        // When: Repository save succeeds
        when(routeRepository.save(any(Mono.class)))
                .thenReturn(Mono.empty());

        // Then: POST creates route with circuit breaker filter
        webTestClient.post()
                .uri("/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("cb-route")
                .jsonPath("$.filters[0].name").isEqualTo("CircuitBreaker")
                .jsonPath("$.filters[0].args.name").isEqualTo("testCircuitBreaker");
    }

    @Test
    void testCreateRoute_RepositoryError() {
        // Given: A valid route request
        RouteRequest request = RouteRequest.builder()
                .id("error-route")
                .uri("http://error.example.com")
                .path("/api/error/**")
                .build();

        // When: Repository save fails
        when(routeRepository.save(any(Mono.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        // Then: POST returns 500 Internal Server Error
        webTestClient.post()
                .uri("/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void testDeleteRoute_Success() {
        // Given: A route ID to delete
        String routeId = "route-to-delete";

        // When: Repository delete succeeds
        when(routeRepository.delete(any(Mono.class)))
                .thenReturn(Mono.empty());

        // Then: DELETE /admin/routes/{id} returns 204 No Content
        webTestClient.delete()
                .uri("/admin/routes/{id}", routeId)
                .exchange()
                .expectStatus().isNoContent();

        verify(routeRepository).delete(any(Mono.class));
    }

    @Test
    void testDeleteRoute_RepositoryError() {
        // Given: A route ID to delete
        String routeId = "error-route";

        // When: Repository delete fails
        when(routeRepository.delete(any(Mono.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        // Then: DELETE returns 500 Internal Server Error
        webTestClient.delete()
                .uri("/admin/routes/{id}", routeId)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void testCreateRoute_InvalidJson() {
        // When/Then: POST with invalid JSON returns 400 Bad Request
        webTestClient.post()
                .uri("/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{invalid json}")
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    void testListRoutes_RepositoryError() {
        // Given: Repository throws error
        when(routeRepository.getRouteDefinitions())
                .thenReturn(Flux.error(new RuntimeException("Redis connection failed")));

        // When/Then: GET /admin/routes propagates error
        webTestClient.get()
                .uri("/admin/routes")
                .exchange()
                .expectStatus().is5xxServerError();
    }
}
