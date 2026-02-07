package com.neuragate.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

// Configuration for Redis template and in-memory fallback routes
@Configuration
public class RouteConfig {

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {

        StringRedisSerializer serializer = new StringRedisSerializer();

        RedisSerializationContext<String, String> context = RedisSerializationContext
                .<String, String>newSerializationContext(serializer)
                .key(serializer)
                .value(serializer)
                .hashKey(serializer)
                .hashValue(serializer)
                .build();

        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    // Day 6: Emergency fallback routes that load if Redis is unavailable
    // Day 8: Enhanced with circuit breaker filters for protection
    // These are critical routes that should always be available for graceful
    // degradation
    //
    // Why these specific routes?
    // 1. Maintenance page - inform users of degraded state
    // 2. Health endpoint - allow monitoring even when Redis is down
    // 3. Critical auth service - maintain security even in degraded mode
    @Bean
    public List<RouteDefinition> fallbackRoutes() {
        // Route 1: Maintenance/Status Page with circuit breaker
        RouteDefinition maintenanceRoute = new RouteDefinition();
        maintenanceRoute.setId("emergency-maintenance");
        maintenanceRoute.setUri(URI.create("http://httpbin.org")); // Replace with actual maintenance service
        maintenanceRoute.setOrder(1);

        PredicateDefinition maintenancePredicate = new PredicateDefinition();
        maintenancePredicate.setName("Path");
        maintenancePredicate.addArg("pattern", "/status/**");
        maintenanceRoute.setPredicates(Arrays.asList(maintenancePredicate));

        // Add circuit breaker and strip prefix filters
        FilterDefinition maintenanceCB = new FilterDefinition();
        maintenanceCB.setName("CircuitBreaker");
        maintenanceCB.addArg("name", "backendService");
        maintenanceCB.addArg("fallbackUri", "forward:/fallback/backend");

        FilterDefinition maintenanceFilter = new FilterDefinition();
        maintenanceFilter.setName("StripPrefix");
        maintenanceFilter.addArg("parts", "1");

        maintenanceRoute.setFilters(Arrays.asList(maintenanceCB, maintenanceFilter));

        // Route 2: Health Check Endpoint (always available, no circuit breaker)
        RouteDefinition healthRoute = new RouteDefinition();
        healthRoute.setId("emergency-health");
        healthRoute.setUri(URI.create("http://localhost:8080"));
        healthRoute.setOrder(0);

        PredicateDefinition healthPredicate = new PredicateDefinition();
        healthPredicate.setName("Path");
        healthPredicate.addArg("pattern", "/actuator/health/**");
        healthRoute.setPredicates(Arrays.asList(healthPredicate));

        // Route 3: Critical Auth Service with lenient circuit breaker
        RouteDefinition authRoute = new RouteDefinition();
        authRoute.setId("emergency-auth");
        authRoute.setUri(URI.create("http://httpbin.org")); // Replace with actual auth service
        authRoute.setOrder(2);

        PredicateDefinition authPredicate = new PredicateDefinition();
        authPredicate.setName("Path");
        authPredicate.addArg("pattern", "/auth/**");
        authRoute.setPredicates(Arrays.asList(authPredicate));

        // Critical services use more lenient circuit breaker
        FilterDefinition authCB = new FilterDefinition();
        authCB.setName("CircuitBreaker");
        authCB.addArg("name", "criticalService");
        authCB.addArg("fallbackUri", "forward:/fallback/critical");

        FilterDefinition authFilter = new FilterDefinition();
        authFilter.setName("StripPrefix");
        authFilter.addArg("parts", "1");

        authRoute.setFilters(Arrays.asList(authCB, authFilter));

        return Arrays.asList(healthRoute, maintenanceRoute, authRoute);
    }
}
