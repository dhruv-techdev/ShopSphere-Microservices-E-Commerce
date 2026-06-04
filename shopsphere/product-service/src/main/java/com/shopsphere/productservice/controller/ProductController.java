package com.shopsphere.productservice.controller;

import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.dto.ProductResponse;
import com.shopsphere.productservice.exception.ApiError;
import com.shopsphere.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product CRUD, search, and filtering")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(
            summary = "Create a new product",
            description = """
                    Creates a product. `categoryId` is optional; if provided, the category must already exist.
                    `active` defaults to `true` when omitted.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Referenced category not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<ProductResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "name": "Wireless Mouse",
                              "description": "Bluetooth, 2.4GHz, ergonomic",
                              "price": 29.99,
                              "categoryId": 1,
                              "stockQuantity": 100,
                              "active": true
                            }
                            """))
            )
            @Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/products/" + created.getId()))
                .body(created);
    }

    @GetMapping
    @Operation(
            summary = "Search and list products",
            description = """
                    All query parameters are optional and combine with AND semantics.

                    - `name` — case-insensitive partial match
                    - `categoryId` — exact category match
                    - `minPrice` / `maxPrice` — inclusive bounds
                    - `active` — true/false
                    - `inStock` — true returns stock > 0, false returns stock == 0
                    - Pagination via `page`, `size`, and `sort` (e.g. `sort=price,desc`)
                    """
    )
    public Page<ProductResponse> search(
            @Parameter(description = "Partial, case-insensitive name match", example = "mouse")
            @RequestParam(required = false) String name,
            @Parameter(description = "Filter by category id", example = "1")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Minimum price (inclusive)", example = "10.00")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price (inclusive)", example = "100.00")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Filter by active flag", example = "true")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "true = stock > 0, false = stock == 0", example = "true")
            @RequestParam(required = false) Boolean inStock,
            @PageableDefault(size = 20) Pageable pageable) {
        return productService.search(name, categoryId, minPrice, maxPrice, active, inStock, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a product",
            description = "Replaces the product's mutable fields. Pass `categoryId: null` to detach the category."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Product or category not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ProductResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a product")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product deleted"),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
