package com.shopsphere.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Health", description = "Service health endpoints")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns service status and current server timestamp.")
    public Map<String, Object> health() {
        return Map.of(
                "service", "order-service",
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }
}
