package com.neuragate.gateway.controller;

import com.neuragate.gateway.model.RouteRequest;
import com.neuragate.repository.RedisRouteDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Day 5: Administrative Control Plane for dynamic route management.
 * 
 * This REST API allows operators to manage gateway routes without redeployment.
 * Routes are persisted in Redis and take effect immediately.
 * 
 * Key design decisions:
 * - Uses RouteRequest DTO to prevent clients from setting system fields
 * - Returns RouteDefinition to show complete route state
 * - Reactive all the way to avoid blocking Virtual Threads
 * - Comprehensive logging for audit trail of route changes
 */
@Slf4j
@RestController
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
public class AdminController {

    private final RedisRouteDefinitionRepository routeRepository;

    /**
     * List all routes currently configured in the gateway.
     * 
     * Returns routes from Redis. If Redis is down, returns fallback routes.
     */
    @GetMapping
    public Flux<RouteDefinition> listRoutes() {
        log.info("Listing all routes");
        return routeRepository.getRouteDefinitions()
                .doOnComplete(() -> log.debug("Successfully listed all routes"))
                .doOnError(e -> log.error("Failed to list routes", e));
    }

    /**
     * Create or update a route.
     * 
     * If a route with the given ID exists, it will be updated.
     * Otherwise, a new route is created.
     * 
     * @param request Route configuration
     * @return 201 with created/updated route
     */
    @PostMapping
    public Mono<ResponseEntity<RouteDefinition>> createOrUpdateRoute(@RequestBody RouteRequest request) {
        log.info("Creating/updating route: {}", request.getId());

        // Convert DTO to Spring Cloud Gateway RouteDefinition
        RouteDefinition route = new RouteDefinition();
        route.setId(request.getId());
        route.setUri(URI.create(request.getUri()));
        route.setOrder(request.getOrder());

        // Set path predicate
        PredicateDefinition pathPredicate = new PredicateDefinition();
        pathPredicate.setName("Path");
        pathPredicate.addArg("pattern", request.getPath());
        route.setPredicates(List.of(pathPredicate));

        // Add circuit breaker filter if specified
        List<FilterDefinition> filters = new ArrayList<>();
        if (request.getCircuitBreakerName() != null) {
            FilterDefinition cbFilter = new FilterDefinition();
            cbFilter.setName("CircuitBreaker");
            cbFilter.addArg("name", request.getCircuitBreakerName());
            cbFilter.addArg("fallbackUri", "forward:/fallback");
            filters.add(cbFilter);
        }
        route.setFilters(filters);

        return routeRepository.save(Mono.just(route))
                .then(Mono.just(route))
                .map(saved -> {
                    log.info("Successfully saved route: {} -> {}", saved.getId(), saved.getUri());
                    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
                })
                .onErrorResume(e -> {
                    log.error("Failed to save route: {}", request.getId(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * Delete a route.
     * 
     * The route is removed from Redis and will no longer be used for routing.
     * 
     * @param id Route identifier
     * @return 204 on success, 500 on error
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteRoute(@PathVariable String id) {
        log.info("Deleting route: {}", id);
        return routeRepository.delete(Mono.just(id))
                .then(Mono.fromCallable(() -> {
                    log.info("Successfully deleted route: {}", id);
                    return ResponseEntity.noContent().<Void>build();
                }))
                .onErrorResume(e -> {
                    log.error("Failed to delete route: {}", id, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
