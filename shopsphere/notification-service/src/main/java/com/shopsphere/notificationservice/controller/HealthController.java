package com.shopsphere.notificationservice.controller;

import com.shopsphere.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Service health endpoints")
public class HealthController {

    private final NotificationService notificationService;

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "notification-service");
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        body.put("totalNotificationsSent", notificationService.countNotifications());
        return body;
    }
}
