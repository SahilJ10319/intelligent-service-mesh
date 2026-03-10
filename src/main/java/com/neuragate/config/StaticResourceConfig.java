package com.neuragate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Static Resource Configuration for Spring Cloud Gateway.
 *
 * Spring Cloud Gateway intercepts all requests before the default
 * static resource handler. This config explicitly routes static
 * file requests (index.html, favicon) to the classpath resources,
 * giving them higher priority than gateway route matching.
 */
@Configuration
public class StaticResourceConfig {

    @Bean
    public RouterFunction<ServerResponse> staticResourceRouter() {
        return RouterFunctions.resources("/**", new ClassPathResource("static/"));
    }
}
