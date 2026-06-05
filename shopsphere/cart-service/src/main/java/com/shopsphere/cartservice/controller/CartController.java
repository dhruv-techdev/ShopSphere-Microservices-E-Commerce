package com.shopsphere.cartservice.controller;

import com.shopsphere.cartservice.dto.AddCartItemRequest;
import com.shopsphere.cartservice.dto.CartResponse;
import com.shopsphere.cartservice.exception.ApiError;
import com.shopsphere.cartservice.service.CartService;
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

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Customer shopping cart APIs")
public class CartController {

    private final CartService cartService;

    @PostMapping("/items")
    @Operation(
            summary = "Add an item to the cart",
            description = """
                    Adds the given productId/quantity to the customer's cart.

                    - Product is fetched from Product Service to validate existence and snapshot name/price.
                    - If the product is already in the cart, its quantity is incremented.
                    - If the product is inactive, the request is rejected with `409 Conflict`.
                    - User identity is provided via `X-User-Id` header for now; will move to JWT in a later story.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Product is inactive",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "Product Service unreachable or errored",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<CartResponse> addItem(
            @Parameter(description = "Customer user id", required = true, example = "1")
            @RequestHeader("X-User-Id") @Positive Long userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "productId": 1, "quantity": 2 }
                            """))
            )
            @Valid @RequestBody AddCartItemRequest request) {
        CartResponse response = cartService.addItem(userId, request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
