package com.neuragate.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Day 26: Reactive Security Configuration
 *
 * Configures a stateless JWT-based security filter chain for Spring Cloud
 * Gateway (WebFlux environment).
 *
 * Authorization rules:
 * - /admin/** → authenticated (ROLE_ADMIN)
 * - /ai/** → authenticated (any valid JWT)
 * - /dashboard/** → authenticated (any valid JWT)
 * - /actuator/prometheus → permitAll (scraped by Prometheus)
 * - /actuator/health → permitAll (load-balancer health checks)
 * - /inventory/** → permitAll (public mock service)
 * - everything else → permitAll (gateway passes through to upstream)
 *
 * Security properties:
 * - Stateless: no session, no cookies (NoOpServerSecurityContextRepository)
 * - CSRF disabled: irrelevant for token-based API
 * - 401/403 responses return JSON-friendly bodies
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationManager jwtAuthenticationManager;
    private final JwtServerAuthenticationConverter jwtConverter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        AuthenticationWebFilter jwtFilter = buildJwtFilter();

        return http
                // ── Stateless: no server-side session storage ──────────────
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

                // ── CSRF disabled (stateless JWT API) ─────────────────────
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // ── Day 27: Granular RBAC authorization rules ──────────────
                .authorizeExchange(exchanges -> exchanges

                        // ADMIN only: stress tests, chaos control, system config
                        .pathMatchers("/admin/**").hasRole(Role.ADMIN.name())

                        // ADVISOR: AI analysis, advice, autonomous actions
                        .pathMatchers("/ai/analyze", "/ai/audit-log", "/ai/prompt").hasRole(Role.ADVISOR.name())

                        // VIEWER: read-only AI endpoints and live dashboard
                        .pathMatchers("/ai/system-prompt").hasAnyRole(Role.ADVISOR.name(), Role.VIEWER.name())
                        .pathMatchers("/dashboard/**")
                        .hasAnyRole(Role.ADMIN.name(), Role.ADVISOR.name(), Role.VIEWER.name())

                        // Public: Prometheus scraping
                        .pathMatchers("/actuator/prometheus").permitAll()
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers("/actuator/info").permitAll()

                        // Public: static dashboard HTML
                        .pathMatchers("/index.html", "/", "/favicon.ico").permitAll()

                        // Public: mock inventory service (chaos targets)
                        .pathMatchers("/inventory/**").permitAll()

                        // Public: token issuance
                        .pathMatchers("/auth/**").permitAll()

                        // All other gateway-proxied routes pass through
                        .anyExchange().permitAll())

                // ── JWT filter ─────────────────────────────────────────────
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                // ── 401 / 403 responses ────────────────────────────────────
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((exchange, e) -> {
                            log.warn("🔒 Unauthorized access to {}: {}",
                                    exchange.getRequest().getPath(), e.getMessage());
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        })
                        .accessDeniedHandler((exchange, e) -> {
                            log.warn("🚫 Access denied to {}: {}",
                                    exchange.getRequest().getPath(), e.getMessage());
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        }))

                .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AuthenticationWebFilter buildJwtFilter() {
        AuthenticationWebFilter filter = new AuthenticationWebFilter(jwtAuthenticationManager);
        filter.setServerAuthenticationConverter(jwtConverter);

        // On failure, return 401 (don't redirect to login page)
        filter.setAuthenticationFailureHandler((exchange, ex) -> {
            log.warn("❌ JWT authentication failed: {}", ex.getMessage());
            exchange.getExchange().getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getExchange().getResponse().setComplete();
        });

        return filter;
    }
}
