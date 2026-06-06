package com.shopsphere.cartservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.cartservice.dto.AddCartItemRequest;
import com.shopsphere.cartservice.dto.CartResponse;
import com.shopsphere.cartservice.dto.UpdateCartItemRequest;
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
                    - If the product is inactive, the request is rejected with 409 Conflict.
                    - User identity is provided via X-User-Id header for now; will move to JWT later.
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
    public CartResponse addItem(
            @Parameter(description = "Customer user id", required = true, example = "1")
            @RequestHeader("X-User-Id") @Positive Long userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "productId": 1, "quantity": 2 }
                            """))
            )
            @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(userId, request);
    }

    @PutMapping("/items/{productId}")
    @Operation(
            summary = "Update the quantity of an item in the cart",
            description = """
                    Sets the absolute quantity for the given product in the cart.

                    - Quantity must be ≥ 1. To remove an item, use DELETE.
                    - Product is re-fetched from Product Service; snapshot fields are refreshed.
                    - Returns 404 if the cart or product is not present.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Cart, item, or product not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Product is inactive",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "Product Service unreachable or errored",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public CartResponse updateItemQuantity(
            @Parameter(description = "Customer user id", required = true, example = "1")
            @RequestHeader("X-User-Id") @Positive Long userId,
            @Parameter(description = "Product id of the cart line to update", required = true, example = "1")
            @PathVariable @Positive Long productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "quantity": 5 }
                            """))
            )
            @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItemQuantity(userId, productId, request);
    }

    @DeleteMapping("/items/{productId}")
    @Operation(
            summary = "Remove an item from the cart",
            description = """
                    Removes the given product from the cart entirely.

                    - Returns the updated cart.
                    - If the cart becomes empty, the cart key in Redis is deleted.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item removed; updated cart returned"),
            @ApiResponse(responseCode = "404", description = "Cart or item not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public CartResponse removeItem(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable @Positive Long productId) {
        return cartService.removeItem(userId, productId);
    }

    @GetMapping
    @Operation(
            summary = "View the current user's cart",
            description = """
                    Returns the current cart contents, computed total, and item count.

                    - If no cart exists for the user, an empty cart is returned (not 404).
                    """
    )
    @ApiResponse(responseCode = "200", description = "Cart returned (possibly empty)")
    public CartResponse getCart(
            @RequestHeader("X-User-Id") @Positive Long userId) {
        return cartService.getCart(userId);
    }

    @DeleteMapping("/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Clear the entire cart",
            description = "Deletes the cart for the user. Returns 404 if no cart exists."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cart cleared"),
            @ApiResponse(responseCode = "404", description = "No cart found for user",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void clearCart(@RequestHeader("X-User-Id") @Positive Long userId) {
        cartService.clearCart(userId);
    }
}
