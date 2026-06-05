package com.shopsphere.inventoryservice.controller;

import com.shopsphere.inventoryservice.dto.InitInventoryRequest;
import com.shopsphere.inventoryservice.dto.StockResponse;
import com.shopsphere.inventoryservice.dto.StockUpdateRequest;
import com.shopsphere.inventoryservice.exception.ApiError;
import com.shopsphere.inventoryservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Stock tracking and updates")
public class InventoryController {

    private final InventoryService inventoryService;

    /* ----------------------------- Health ----------------------------- */

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public Map<String, Object> health() {
        return Map.of(
                "service", "inventory-service",
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }

    /* ----------------------------- Init ----------------------------- */

    @PostMapping
    @Operation(
            summary = "Initialize inventory for a product",
            description = """
                    Creates an inventory record for a product that doesn't have one yet.

                    - Fails with 409 if inventory already exists for that productId.
                    - reservedQuantity starts at 0.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Inventory created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Inventory already exists for this product",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<StockResponse> initInventory(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "productId": 1, "initialQuantity": 100 }
                            """))
            )
            @Valid @RequestBody InitInventoryRequest request) {
        StockResponse created = inventoryService.initInventory(request);
        return ResponseEntity
                .created(URI.create("/api/v1/inventory/" + created.getProductId()))
                .body(created);
    }

    /* ---------------- ST8 — Check stock ---------------- */

    @GetMapping("/{productId}")
    @Operation(
            summary = "Check current stock for a product",
            description = """
                    Returns available, reserved, and sellable quantities along with `inStock` and `lowStock` flags.

                    - `sellableQuantity = availableQuantity - reservedQuantity`
                    - `lowStock` is true when sellable ≤ configured threshold (default 10)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock info returned"),
            @ApiResponse(responseCode = "404", description = "No inventory record for this product",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public StockResponse checkStock(
            @Parameter(description = "Product id", required = true, example = "1")
            @PathVariable @Positive Long productId) {
        return inventoryService.checkStock(productId);
    }

    @GetMapping("/low-stock")
    @Operation(
            summary = "List inventory items at or below the low-stock threshold",
            description = "Useful for admin dashboards. Threshold is configurable via app.inventory.low-stock-threshold."
    )
    public List<StockResponse> listLowStock() {
        return inventoryService.findLowStock();
    }

    /* ---------------- ST7 — Update stock ---------------- */

    @PutMapping("/{productId}")
    @Operation(
            summary = "Update stock for a product",
            description = """
                    Adjusts `availableQuantity` using one of three operations:

                    - `SET` — replaces the value entirely (use for receiving new shipments)
                    - `INCREMENT` — adds (use for customer returns)
                    - `DECREMENT` — subtracts (use for manual write-offs)

                    DECREMENT is rejected if the result would fall below current `reservedQuantity`.
                    Concurrent updates are protected with pessimistic locking + an @Version optimistic check.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No inventory record for this product",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409",
                    description = "Insufficient stock for decrement, or concurrent modification — retry",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public StockResponse updateStock(
            @Parameter(description = "Product id", required = true, example = "1")
            @PathVariable @Positive Long productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = {
                            @ExampleObject(name = "SET", value = """
                                    { "operation": "SET", "quantity": 50 }
                                    """),
                            @ExampleObject(name = "INCREMENT", value = """
                                    { "operation": "INCREMENT", "quantity": 10 }
                                    """),
                            @ExampleObject(name = "DECREMENT", value = """
                                    { "operation": "DECREMENT", "quantity": 5 }
                                    """)
                    })
            )
            @Valid @RequestBody StockUpdateRequest request) {
        return inventoryService.updateStock(productId, request);
    }
}
