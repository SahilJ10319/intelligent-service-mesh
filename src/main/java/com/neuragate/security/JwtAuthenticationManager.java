package com.neuragate.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Day 26: JWT Authentication Manager
 *
 * Implements ReactiveAuthenticationManager to validate incoming JWT tokens.
 * Spring Security calls this after JwtServerAuthenticationConverter extracts
 * the bearer token from the Authorization header.
 *
 * Validates:
 * - JWT signature (HMAC-SHA256 with configured secret)
 * - Token expiration
 * - Extracts roles from claims → maps to GrantedAuthority
 *
 * Error handling:
 * - Expired token → 401 Unauthorized
 * - Invalid signature → 401 Unauthorized
 * - Malformed token → 401 Unauthorized
 */
@Slf4j
@Component
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    @Value("${neuragate.security.jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = authentication.getCredentials().toString();

        return Mono.fromCallable(() -> validate(token))
                .onErrorMap(e -> {
                    log.warn("JWT validation failed: {}", e.getMessage());
                    return new BadCredentialsException("Invalid or expired JWT token", e);
                });
    }

    // ── internal ─────────────────────────────────────────────────────────────

    private Authentication validate(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new BadCredentialsException("JWT parse error: " + e.getMessage(), e);
        }

        // Check expiration explicitly (jjwt throws ExpiredJwtException, but
        // belt-and-suspenders)
        if (claims.getExpiration() != null && claims.getExpiration().before(new Date())) {
            throw new BadCredentialsException("JWT token has expired");
        }

        String subject = claims.getSubject();

        // Extract roles claim; default to ROLE_USER if absent
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        if (roles == null)
            roles = Collections.singletonList("ROLE_USER");

        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        log.debug("✅ JWT authenticated: subject={}, roles={}", subject, roles);

        return new UsernamePasswordAuthenticationToken(subject, token, authorities);
    }

    /**
     * Utility: generate a test token (useful for /ai/token endpoint or tests).
     */
    public String generateToken(String subject, List<String> roles, long expirationMs) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }
}
