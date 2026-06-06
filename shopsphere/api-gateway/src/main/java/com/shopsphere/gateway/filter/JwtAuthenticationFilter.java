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

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final SecurityPaths securityPaths;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod() == null ? "GET" : request.getMethod().name();

        // ST2 — public routes pass through untouched
        if (securityPaths.isPublic(path, method)) {
            return chain.filter(exchange);
        }

        // Authorization header required for everything else
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        Claims claims;
        try {
            claims = jwtUtil.parseAndValidate(token);
        } catch (Exception ex) {
            log.debug("JWT validation failed for {} {}: {}", method, path, ex.getMessage());
            return unauthorized(exchange, "Invalid or expired JWT");
        }

        // ST5 — extract user id and role
        Long userId = claims.get("userId", Long.class);
        String role = claims.get("role", String.class);
        String email = claims.getSubject();

        if (userId == null || role == null) {
            return unauthorized(exchange, "JWT missing required claims");
        }

        // ST4 — admin-only enforcement on write methods
        if (securityPaths.requiresAdmin(path, method) && !"ADMIN".equalsIgnoreCase(role)) {
            return forbidden(exchange, "ADMIN role required for " + method + " " + path);
        }

        // ST6 — propagate user context to downstream services as headers
        ServerHttpRequest mutated = request.mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Role", role)
                .header("X-User-Email", email)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Gateway-Error", message);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add("X-Gateway-Error", message);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;  // run before route filter
    }
}
