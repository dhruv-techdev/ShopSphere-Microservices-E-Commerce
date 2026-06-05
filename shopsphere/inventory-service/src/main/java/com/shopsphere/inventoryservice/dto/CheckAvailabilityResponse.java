package com.shopsphere.inventoryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckAvailabilityResponse {

    private boolean allAvailable;
    private List<ItemAvailability> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemAvailability {
        private Long productId;
        private Integer requestedQuantity;
        private Integer sellableQuantity;
        private boolean available;
        private String reason;
    }
}
