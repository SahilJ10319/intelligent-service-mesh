package com.neuragate.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Day 26: Auth Controller
 *
 * Provides a simple /auth/token endpoint so developers can obtain a JWT for
 * testing the secured /ai/** and /admin/** endpoints.
 *
 * POST /auth/token
 * Body: { "username": "sahil", "password": "neuragate" }
 * Response: { "token": "<jwt>" }
 *
 * NOTE: Credentials are checked against application.properties values.
 * Production systems should use a proper user store / OAuth2.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtAuthenticationManager jwtAuthenticationManager;

    @Value("${neuragate.security.jwt.dev-username:neuragate}")
    private String devUsername;

    @Value("${neuragate.security.jwt.dev-password:secret}")
    private String devPassword;

    @Value("${neuragate.security.jwt.expiration-ms:3600000}")
    private long expirationMs;

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> token(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        // Check dev credentials
        if (devUsername.equals(username) && devPassword.equals(password)) {
            List<String> roles = List.of("ROLE_ADMIN");
            String token = jwtAuthenticationManager.generateToken(username, roles, expirationMs);
            log.info("🎫 Issued JWT for dev user: {} (roles: {})", username, roles);
            return ResponseEntity.ok(Map.of("token", token, "expiresInMs", expirationMs, "roles", roles));
        }

        // Check RBAC test users: admin/admin123, advisor/advisor123, viewer/viewer123
        Map<String, List<String>> testUsers = Map.of(
                "admin:admin123", List.of("ROLE_ADMIN"),
                "advisor:advisor123", List.of("ROLE_ADVISOR"),
                "viewer:viewer123", List.of("ROLE_VIEWER")
        );

        String key = username + ":" + password;
        if (testUsers.containsKey(key)) {
            List<String> roles = testUsers.get(key);
            String token = jwtAuthenticationManager.generateToken(username, roles, expirationMs);
            log.info("🎫 Issued JWT for test user: {} (roles: {})", username, roles);
            return ResponseEntity.ok(Map.of("token", token, "expiresInMs", expirationMs, "roles", roles));
        }

        log.warn("🔒 Invalid credentials for user: {}", username);
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }
}
