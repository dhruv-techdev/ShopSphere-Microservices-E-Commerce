package com.shopsphere.orderservice.dto;

import com.shopsphere.orderservice.entity.CancellationReason;
import com.shopsphere.orderservice.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private Long id;
    private Long userId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private Integer itemCount;
    /** Null only for orders placed before US33. */
    private AddressDto shippingAddress;
    /** US36 — null until shipping-service dispatches the order. */
    private Long shipmentId;
    private String carrier;
    private String trackingNumber;
    private Instant shippedAt;
    private Instant deliveredAt;
    /** US38 — set only when status is CANCELLED. */
    private CancellationReason cancellationReason;
    private String cancellationDescription;
    private Instant cancelledAt;
    private List<OrderItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;
}
