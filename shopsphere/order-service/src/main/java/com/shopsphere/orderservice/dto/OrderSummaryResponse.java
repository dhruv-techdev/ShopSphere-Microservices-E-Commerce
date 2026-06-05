package com.shopsphere.orderservice.dto;

import com.shopsphere.orderservice.entity.OrderStatus;
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
public class OrderSummaryResponse {
    private Long id;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private Instant createdAt;
}
