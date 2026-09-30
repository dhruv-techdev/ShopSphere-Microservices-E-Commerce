package com.shopsphere.security;

/**
 * The caller, as proven by a verified JWT. Available in controllers via
 * {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(Long userId, String email, String role) {

    public static final String ADMIN = "ADMIN";

    public boolean isAdmin() {
        return ADMIN.equals(role);
    }
}
