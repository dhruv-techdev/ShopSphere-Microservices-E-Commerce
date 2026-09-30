package com.shopsphere.gateway.filter;

import com.shopsphere.gateway.config.JwtUtil;
import com.shopsphere.gateway.config.SecurityPaths;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "gateway-test-secret-gateway-test-secret-0123456789";

    private JwtAuthenticationFilter filter;
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.invokeMethod(jwtUtil, "init");

        SecurityPaths paths = new SecurityPaths();
        paths.setPublicPaths(List.of("/api/v1/auth/login"));
        paths.setPublicGetPrefixes(List.of("/api/v1/products"));
        paths.setAdminWritePrefixes(List.of("/api/v1/products"));
        paths.setAdminPrefixes(List.of("/api/v1/admin"));

        filter = new JwtAuthenticationFilter(jwtUtil, paths);
        forwarded.set(null);
    }

    private static String bearer(long userId, String role) {
        return "Bearer " + Jwts.builder()
                .subject("user" + userId + "@example.com")
                .claim("userId", userId)
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private HttpHeaders forwardedHeaders() {
        return forwarded.get().getRequest().getHeaders();
    }

    @Test
    void customer_onAdminPath_is403_andNotForwarded() {
        var exchange = run(MockServerHttpRequest.get("/api/v1/admin/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(7, "CUSTOMER")));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void customer_writingProducts_is403() {
        var exchange = run(MockServerHttpRequest.post("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(7, "CUSTOMER")));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_onAdminPath_isForwardedWithRoleAndToken() {
        run(MockServerHttpRequest.get("/api/v1/admin/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(1, "ADMIN")));

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwardedHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
        assertThat(forwardedHeaders().getFirst("X-User-Id")).isEqualTo("1");
        assertThat(forwardedHeaders().getFirst(HttpHeaders.AUTHORIZATION)).startsWith("Bearer ");
    }

    @Test
    void noToken_onProtectedPath_is401() {
        var exchange = run(MockServerHttpRequest.get("/api/v1/orders/my-orders"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void spoofedIdentityHeaders_onPublicPath_areStripped() {
        run(MockServerHttpRequest.get("/api/v1/products")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Id", "1"));

        assertThat(forwardedHeaders().containsKey("X-User-Role")).isFalse();
        assertThat(forwardedHeaders().containsKey("X-User-Id")).isFalse();
    }

    @Test
    void spoofedUserId_isReplacedByTheTokensUserId() {
        run(MockServerHttpRequest.get("/api/v1/orders/my-orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(7, "CUSTOMER"))
                .header("X-User-Id", "999")
                .header("X-User-Role", "ADMIN"));

        assertThat(forwardedHeaders().get("X-User-Id")).containsExactly("7");
        assertThat(forwardedHeaders().get("X-User-Role")).containsExactly("CUSTOMER");
    }
}
