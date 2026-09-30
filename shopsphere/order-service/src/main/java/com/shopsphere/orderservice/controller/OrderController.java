package com.shopsphere.orderservice.controller;

import com.shopsphere.orderservice.dto.CreateOrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.OrderSummaryResponse;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.exception.ApiError;
import com.shopsphere.orderservice.service.OrderService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Customer order lifecycle APIs")
public class OrderController {

    private final OrderService orderService;

    /* ----------------------------- Create ----------------------------- */

    @PostMapping
    @Operation(
            summary = "Create an order from the user's cart",
            description = """
                    Creates a new order containing all items currently in the user's cart.

                    Flow:
                    1. Validates the request, including the required shipping address.
                    2. Fetches the cart from Cart Service.
                    3. Checks stock with Inventory Service.
                    4. Re-fetches each product from Product Service to validate availability.
                    5. Snapshots product name, current price and the shipping address onto the order.
                    6. Saves the order in PENDING_PAYMENT status and publishes order.created after commit.
                    7. Clears the user's cart (best-effort).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created in PENDING_PAYMENT status"),
            @ApiResponse(responseCode = "400", description = "Missing/invalid shipping address, empty cart or malformed body",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "A product in the cart no longer exists",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Product inactive or insufficient stock",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "Cart, Product or Inventory Service unavailable",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Parameter(description = "Customer user id", required = true, example = "1")
            @RequestHeader("X-User-Id") @Positive Long userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "shippingAddress": {
                                "recipientName": "Jane Doe",
                                "phone": "+1 416 555 0199",
                                "line1": "123 King St W",
                                "line2": "Unit 4",
                                "city": "Toronto",
                                "state": "ON",
                                "postalCode": "M5V 3L9",
                                "country": "CA"
                              },
                              "notes": "Please leave at the front door"
                            }
                            """))
            )
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(userId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + response.getId()))
                .body(response);
    }

    /* ----------------------- ST1 + ST2 — List ----------------------- */

    @GetMapping("/my-orders")
    @Operation(
            summary = "List the current user's orders",
            description = """
                    Returns the authenticated customer's orders, newest first.

                    - Optional `status` filter (PENDING_PAYMENT, PAID, PAYMENT_FAILED, SHIPPED, DELIVERED, CANCELLED).
                    - Pagination via `page`, `size`. Sort is fixed to createdAt desc.
                    - Returns lightweight summaries (no line items). Use `GET /{orderId}` for full details.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Page of order summaries (possibly empty)")
    public Page<OrderSummaryResponse> getMyOrders(
            @Parameter(description = "Customer user id", required = true, example = "1")
            @RequestHeader("X-User-Id") @Positive Long userId,
            @Parameter(description = "Optional status filter")
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getMyOrders(userId, status, pageable);
    }

    /* ----------------------- ST3 + ST4 + ST5 + ST6 — Details ----------------------- */

    @GetMapping("/{orderId}")
    @Operation(
            summary = "Get details for a specific order",
            description = """
                    Returns the full order with all line items and the shipping address.

                    - The authenticated user must own this order, otherwise 403 Forbidden.
                    - Items include snapshotted product name and unit price from the time of order.
                    - shippingAddress is null only for orders placed before shipping addresses existed.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order details with all items"),
            @ApiResponse(responseCode = "403", description = "Order belongs to another user",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public OrderResponse getOrderById(
            @Parameter(description = "Customer user id", required = true, example = "1")
            @RequestHeader("X-User-Id") @Positive Long userId,
            @Parameter(description = "Order id", required = true, example = "1")
            @PathVariable @Positive Long orderId) {
        return orderService.getOrderById(userId, orderId);
    }
}
