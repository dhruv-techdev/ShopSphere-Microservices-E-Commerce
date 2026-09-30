package com.shopsphere.productservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {
    private Long id;
    private String name;
    private String description;
    private Instant createdAt;

    /** US44 — number of products in this category. Omitted when nested inside a ProductResponse. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long productCount;
}
