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

    // In-memory fallback routes that load if Redis is unavailable
    // These are critical routes that should always be available
    @Bean
    public List<RouteDefinition> fallbackRoutes() {
        RouteDefinition httpbinRoute = new RouteDefinition();
        httpbinRoute.setId("fallback-httpbin");
        httpbinRoute.setUri(URI.create("http://httpbin.org"));

        PredicateDefinition predicate = new PredicateDefinition();
        predicate.setName("Path");
        predicate.addArg("pattern", "/fallback/**");
        httpbinRoute.setPredicates(Arrays.asList(predicate));

        FilterDefinition filter = new FilterDefinition();
        filter.setName("StripPrefix");
        filter.addArg("parts", "1");
        httpbinRoute.setFilters(Arrays.asList(filter));

        return Arrays.asList(httpbinRoute);
    }
}
