package com.shopsphere.inventoryservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockUpdateRequest {

    @NotNull(message = "operation is required")
    private Operation operation;

    /** For SET this is the new absolute value; for INCREMENT/DECREMENT this is the delta. Must be >= 0. */
    @NotNull(message = "quantity is required")
    @Min(value = 0, message = "quantity cannot be negative")
    private Integer quantity;

    public enum Operation {
        SET,
        INCREMENT,
        DECREMENT
    }
}
