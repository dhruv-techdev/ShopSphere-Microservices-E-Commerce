package com.shopsphere.orderservice.dto;

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
    private List<OrderItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;
}
