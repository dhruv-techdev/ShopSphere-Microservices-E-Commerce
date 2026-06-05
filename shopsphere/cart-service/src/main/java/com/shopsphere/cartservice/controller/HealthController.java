package com.shopsphere.cartservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Service and Redis health endpoints")
public class HealthController {

    private final RedisConnectionFactory redisConnectionFactory;

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns service status, current timestamp, and Redis ping result.")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "cart-service");
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        try {
            String pong = redisConnectionFactory.getConnection().ping();
            body.put("redis", "UP");
            body.put("redisPing", pong);
        } catch (Exception ex) {
            body.put("redis", "DOWN");
            body.put("redisError", ex.getMessage());
            body.put("status", "DEGRADED");
        }
        return body;
    }
}
