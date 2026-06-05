package com.shopsphere.orderservice.controller;

import com.shopsphere.orderservice.dto.CreateOrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Customer order lifecycle APIs")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(
            summary = "Create an order from the user's cart",
            description = """
                    Creates a new order containing all items currently in the user's cart.

                    Flow:
                    1. Fetches the cart from Cart Service.
                    2. Re-fetches each product from Product Service to validate availability and stock.
                    3. Snapshots product name and current price into immutable order line items.
                    4. Saves the order in PENDING_PAYMENT status.
                    5. Clears the user's cart (best-effort; failure does not roll back the order).

                    User identity is provided via `X-User-Id` header for now; will move to JWT later.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created in PENDING_PAYMENT status"),
            @ApiResponse(responseCode = "400", description = "Cart is empty or request invalid",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "A product in the cart no longer exists",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Product inactive or insufficient stock",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "Cart or Product Service unavailable",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Parameter(description = "Customer user id", required = true, example = "1")
            @RequestHeader("X-User-Id") @Positive Long userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = false,
                    content = @Content(examples = @ExampleObject(value = """
                            { "notes": "Please leave at the front door" }
                            """))
            )
            @Valid @RequestBody(required = false) CreateOrderRequest request) {
        CreateOrderRequest body = request == null ? new CreateOrderRequest() : request;
        OrderResponse response = orderService.createOrder(userId, body);
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + response.getId()))
                .body(response);
    }
}
