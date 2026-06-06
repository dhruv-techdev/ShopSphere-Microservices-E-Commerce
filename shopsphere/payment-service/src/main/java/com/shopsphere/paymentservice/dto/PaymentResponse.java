package com.shopsphere.paymentservice.dto;

import com.shopsphere.paymentservice.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private String paymentReference;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String message;
    private Instant createdAt;
    private Instant updatedAt;
}
