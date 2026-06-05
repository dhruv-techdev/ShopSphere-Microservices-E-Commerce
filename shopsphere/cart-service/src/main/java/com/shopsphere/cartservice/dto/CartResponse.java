package com.shopsphere.cartservice.dto;

import com.shopsphere.cartservice.model.CartItem;
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
public class CartResponse {
    private Long userId;
    private List<CartItem> items;
    private BigDecimal total;
    private Integer itemCount;
    private Instant createdAt;
    private Instant updatedAt;
}
