package com.neuragate.security;

/**
 * Day 27: Role Definitions
 *
 * Defines the three RBAC roles used across the gateway security layer.
 * Spring Security expects role constants to carry the "ROLE_" prefix when
 * used with hasRole(), so each enum value maps to its prefixed authority via
 * authority().
 *
 * Role hierarchy (for documentation only — not enforced with RoleHierarchy):
 * ADMIN ⊃ ADVISOR ⊃ VIEWER
 *
 * Usage in SecurityConfig:
 * .pathMatchers("/admin/**").hasRole(Role.ADMIN.name())
 * .pathMatchers("/ai/analyze").hasRole(Role.ADVISOR.name())
 * .pathMatchers("/dashboard/**").hasRole(Role.VIEWER.name())
 *
 * Usage in JwtAuthenticationManager (claim value in token):
 * roles: ["ROLE_ADMIN"] → full access
 * roles: ["ROLE_ADVISOR"] → /ai/** access
 * roles: ["ROLE_VIEWER"] → /dashboard/** read-only access
 */
public enum Role {

    /** Full system access — can trigger stress tests, change config, view all */
    ADMIN,

    /** Can invoke AI analysis and read audit logs */
    ADVISOR,

    /** Can view the live dashboard and read metrics */
    VIEWER;

    /** Returns the Spring Security authority string (e.g. "ROLE_ADMIN") */
    public String authority() {
        return "ROLE_" + name();
    }
}
