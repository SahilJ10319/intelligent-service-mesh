package com.neuragate.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

// Redis-backed repository for dynamic route definitions
// Implements anti-fragile pattern: falls back to in-memory routes if Redis is down
@Repository
public class RedisRouteDefinitionRepository implements RouteDefinitionRepository {

    private static final Logger logger = LoggerFactory.getLogger(RedisRouteDefinitionRepository.class);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final List<RouteDefinition> fallbackRoutes;
    private final String routeKey;

    public RedisRouteDefinitionRepository(
            ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            List<RouteDefinition> fallbackRoutes,
            @Value("${metadata.route-key}") String routeKey) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.fallbackRoutes = fallbackRoutes;
        this.routeKey = routeKey;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return redisTemplate.opsForHash()
                .values(routeKey)
                .map(this::deserializeRoute)
                .onErrorResume(error -> {
                    logger.warn("Redis unavailable, using fallback routes: {}", error.getMessage());
                    return Flux.fromIterable(fallbackRoutes);
                });
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(r -> {
            try {
                String json = objectMapper.writeValueAsString(r);
                return redisTemplate.opsForHash()
                        .put(routeKey, r.getId(), json)
                        .then();
            } catch (JsonProcessingException e) {
                return Mono.error(e);
            }
        });
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id -> redisTemplate.opsForHash()
                .remove(routeKey, id)
                .then());
    }

    private RouteDefinition deserializeRoute(Object json) {
        try {
            return objectMapper.readValue(json.toString(), RouteDefinition.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize route", e);
        }
    }
}
