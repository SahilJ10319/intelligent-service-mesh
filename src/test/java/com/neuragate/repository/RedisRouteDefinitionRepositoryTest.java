package com.neuragate.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Day 7: Unit tests for RedisRouteDefinitionRepository.
 * 
 * Tests the reactive repository using StepVerifier to verify:
 * - Saving routes to Redis
 * - Retrieving all routes from Redis
 * - Deleting routes from Redis
 * - Fallback behavior when Redis is unavailable
 * 
 * Why StepVerifier?
 * - Designed specifically for testing reactive streams
 * - Verifies emissions, completion, and errors
 * - Ensures proper reactive behavior (non-blocking)
 */
@ExtendWith(MockitoExtension.class)
class RedisRouteDefinitionRepositoryTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveHashOperations<String, Object, Object> hashOperations;

    private ObjectMapper objectMapper;
    private List<RouteDefinition> fallbackRoutes;
    private RedisRouteDefinitionRepository repository;

    private static final String ROUTE_KEY = "gateway_routes";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Create fallback routes for testing
        RouteDefinition fallbackRoute = new RouteDefinition();
        fallbackRoute.setId("fallback-route");
        fallbackRoute.setUri(URI.create("http://fallback.example.com"));
        fallbackRoutes = Arrays.asList(fallbackRoute);

        // Mock Redis template to return hash operations
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        repository = new RedisRouteDefinitionRepository(
                redisTemplate,
                objectMapper,
                fallbackRoutes,
                ROUTE_KEY);
    }

    @Test
    void testSaveRoute_Success() throws Exception {
        // Given: A route to save
        RouteDefinition route = createTestRoute("test-route", "http://test.example.com", "/test/**");
        String expectedJson = objectMapper.writeValueAsString(route);

        // When: Redis PUT succeeds
        when(hashOperations.put(eq(ROUTE_KEY), eq("test-route"), eq(expectedJson)))
                .thenReturn(Mono.just(true));

        // Then: Save operation completes successfully
        StepVerifier.create(repository.save(Mono.just(route)))
                .verifyComplete();

        // Verify Redis was called
        verify(hashOperations).put(ROUTE_KEY, "test-route", expectedJson);
    }

    @Test
    void testSaveRoute_RedisError() {
        // Given: A route to save
        RouteDefinition route = createTestRoute("test-route", "http://test.example.com", "/test/**");

        // When: Redis PUT fails
        when(hashOperations.put(anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        // Then: Save operation propagates the error
        StepVerifier.create(repository.save(Mono.just(route)))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void testGetRouteDefinitions_Success() throws Exception {
        // Given: Routes exist in Redis
        RouteDefinition route1 = createTestRoute("route-1", "http://service1.example.com", "/api/v1/**");
        RouteDefinition route2 = createTestRoute("route-2", "http://service2.example.com", "/api/v2/**");

        String json1 = objectMapper.writeValueAsString(route1);
        String json2 = objectMapper.writeValueAsString(route2);

        // When: Redis returns route JSON
        when(hashOperations.values(ROUTE_KEY))
                .thenReturn(Flux.just(json1, json2));

        // Then: Repository returns deserialized routes
        StepVerifier.create(repository.getRouteDefinitions())
                .expectNextMatches(r -> r.getId().equals("route-1"))
                .expectNextMatches(r -> r.getId().equals("route-2"))
                .verifyComplete();
    }

    @Test
    void testGetRouteDefinitions_RedisUnavailable_UsesFallback() {
        // Given: Redis is unavailable
        when(hashOperations.values(ROUTE_KEY))
                .thenReturn(Flux.error(new RuntimeException("Redis connection failed")));

        // When: Getting routes
        // Then: Repository falls back to in-memory routes
        StepVerifier.create(repository.getRouteDefinitions())
                .expectNextMatches(r -> r.getId().equals("fallback-route"))
                .verifyComplete();
    }

    @Test
    void testGetRouteDefinitions_EmptyRedis() {
        // Given: Redis is empty
        when(hashOperations.values(ROUTE_KEY))
                .thenReturn(Flux.empty());

        // When: Getting routes
        // Then: Repository returns empty flux
        StepVerifier.create(repository.getRouteDefinitions())
                .verifyComplete();
    }

    @Test
    void testDeleteRoute_Success() {
        // Given: A route ID to delete
        String routeId = "route-to-delete";

        // When: Redis DELETE succeeds
        when(hashOperations.remove(ROUTE_KEY, routeId))
                .thenReturn(Mono.just(1L));

        // Then: Delete operation completes successfully
        StepVerifier.create(repository.delete(Mono.just(routeId)))
                .verifyComplete();

        // Verify Redis was called
        verify(hashOperations).remove(ROUTE_KEY, routeId);
    }

    @Test
    void testDeleteRoute_NonExistentRoute() {
        // Given: A non-existent route ID
        String routeId = "non-existent";

        // When: Redis DELETE returns 0 (not found)
        when(hashOperations.remove(ROUTE_KEY, routeId))
                .thenReturn(Mono.just(0L));

        // Then: Delete operation still completes (idempotent)
        StepVerifier.create(repository.delete(Mono.just(routeId)))
                .verifyComplete();
    }

    @Test
    void testDeleteRoute_RedisError() {
        // Given: A route ID to delete
        String routeId = "test-route";

        // When: Redis DELETE fails
        when(hashOperations.remove(ROUTE_KEY, routeId))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        // Then: Delete operation propagates the error
        StepVerifier.create(repository.delete(Mono.just(routeId)))
                .expectError(RuntimeException.class)
                .verify();
    }

    /**
     * Helper method to create a test route with common configuration.
     */
    private RouteDefinition createTestRoute(String id, String uri, String path) {
        RouteDefinition route = new RouteDefinition();
        route.setId(id);
        route.setUri(URI.create(uri));

        PredicateDefinition predicate = new PredicateDefinition();
        predicate.setName("Path");
        predicate.addArg("pattern", path);
        route.setPredicates(Arrays.asList(predicate));

        return route;
    }
}
