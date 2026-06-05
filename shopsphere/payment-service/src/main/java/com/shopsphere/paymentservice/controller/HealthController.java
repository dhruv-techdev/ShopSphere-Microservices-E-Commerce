package com.shopsphere.paymentservice.controller;

import com.shopsphere.paymentservice.service.PaymentService;
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
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Service health endpoints")
public class HealthController {

    private final PaymentService paymentService;

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "payment-service");
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        body.put("totalPaymentsProcessed", paymentService.countPayments());
        return body;
    }
}
