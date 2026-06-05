package com.shopsphere.orderservice.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    /** Optional shipping note or customer instruction. Reserved for future use. */
    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;
}
