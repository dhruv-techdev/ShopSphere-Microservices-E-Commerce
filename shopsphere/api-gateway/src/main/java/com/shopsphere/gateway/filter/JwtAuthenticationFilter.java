package com.shopsphere.gateway.filter;

import com.shopsphere.gateway.config.JwtUtil;
import com.shopsphere.gateway.config.SecurityPaths;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

/**
 * Validates the JWT at the edge and propagates identity downstream.
 *
 * <p>US42 hardening:
 * <ul>
 *   <li>Client-supplied X-User-Id / X-User-Role / X-User-Email are stripped on EVERY request
 *       (public ones included) — they are only ever set from a validated token.</li>
 *   <li>The Authorization header is forwarded unchanged so services verify it again
 *       (security-lib) — the gateway is not the only line of defence.</li>
 *   <li>{@code admin-prefixes} make whole path trees ADMIN-only for every method.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    static final String USER_ID = "X-User-Id";
    static final String USER_ROLE = "X-User-Role";
    static final String USER_EMAIL = "X-User-Email";
    private static final List<String> IDENTITY_HEADERS = List.of(USER_ID, USER_ROLE, USER_EMAIL);

    private final JwtUtil jwtUtil;
    private final SecurityPaths securityPaths;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest original = exchange.getRequest();
        String path = original.getURI().getPath();
        String method = original.getMethod() == null ? "GET" : original.getMethod().name();

        // Never trust identity headers coming from the client.
        ServerHttpRequest sanitized = original.mutate()
                .headers(headers -> IDENTITY_HEADERS.forEach(headers::remove))
                .build();

        if (securityPaths.isPublic(path, method)) {
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String authHeader = original.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing or malformed Authorization header");
        }

        Claims claims;
        try {
            claims = jwtUtil.parseAndValidate(authHeader.substring(BEARER_PREFIX.length()));
        } catch (Exception ex) {
            log.debug("JWT validation failed for {} {}: {}", method, path, ex.getMessage());
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT");
        }

        Long userId = claims.get("userId", Long.class);
        String role = claims.get("role", String.class);
        String email = claims.getSubject();
        if (userId == null || role == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "JWT missing required claims");
        }
        role = role.toUpperCase(Locale.ROOT);

        if (securityPaths.requiresAdmin(path, method) && !"ADMIN".equals(role)) {
            log.info("Forbidden: userId={} role={} attempted {} {}", userId, role, method, path);
            return reject(exchange, HttpStatus.FORBIDDEN, "ADMIN role required for " + method + " " + path);
        }

        ServerHttpRequest authenticated = sanitized.mutate()
                .header(USER_ID, String.valueOf(userId))
                .header(USER_ROLE, role)
                .header(USER_EMAIL, email == null ? "" : email)
                .build();

        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("X-Gateway-Error", message);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;  // run before route filter
    }
}
