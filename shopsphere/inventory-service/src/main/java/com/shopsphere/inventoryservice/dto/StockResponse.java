package com.shopsphere.inventoryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockResponse {
    private Long productId;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer sellableQuantity;
    private Boolean inStock;
    private Boolean lowStock;
    private Instant updatedAt;
}
