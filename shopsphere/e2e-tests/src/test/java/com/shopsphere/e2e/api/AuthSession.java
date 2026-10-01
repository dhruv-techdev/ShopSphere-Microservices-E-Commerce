package com.shopsphere.e2e.api;

/** Result of POST /api/v1/auth/login. */
public record AuthSession(long userId, String email, String role, String accessToken) {

    public String bearer() {
        return "Bearer " + accessToken;
    }

    @Override
    public String toString() {
        return "AuthSession[userId=" + userId + ", email=" + email + ", role=" + role + "]";
    }
}
