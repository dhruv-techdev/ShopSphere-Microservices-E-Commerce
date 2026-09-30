package com.shopsphere.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Verifies tokens issued by user-service (HS256, claims: sub=email, userId, role).
 * Signature and expiry are checked by jjwt; anything invalid yields empty.
 */
public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = claims.get("userId", Long.class);
            String role = claims.get("role", String.class);
            if (userId == null || role == null || role.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(userId, claims.getSubject(), role.toUpperCase(Locale.ROOT)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
