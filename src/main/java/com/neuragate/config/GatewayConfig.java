package com.neuragate.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Gateway-specific configuration for routing
@Configuration
public class GatewayConfig {

    // Fluent API for defining routes programmatically
    // Currently using application.properties for routes, but this is ready for
    // future dynamic routing
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Routes are currently defined in application.properties
                // This bean is a placeholder for future dynamic routing logic
                .build();
    }
}
