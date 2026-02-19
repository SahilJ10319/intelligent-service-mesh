package com.neuragate.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Day 26: JWT Server Authentication Converter
 *
 * Extracts the Bearer token from the Authorization header and wraps it in an
 * Authentication object so the JwtAuthenticationManager can validate it.
 *
 * Flow:
 * 1. Client sends: Authorization: Bearer <jwt>
 * 2. This converter strips "Bearer " and returns an unauthenticated token
 * 3. JwtAuthenticationManager.authenticate() validates the raw JWT string
 * 4. Spring Security stores the resulting authenticated token in the context
 *
 * Returns Mono.empty() (→ 401) when the header is missing or malformed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtServerAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(header -> header.startsWith(BEARER_PREFIX))
                .map(header -> {
                    String token = header.substring(BEARER_PREFIX.length()).trim();
                    log.debug("🔑 Extracted bearer token from Authorization header");
                    // Return unauthenticated token; manager validates it
                    return (Authentication) new UsernamePasswordAuthenticationToken(token, token);
                });
    }
}
