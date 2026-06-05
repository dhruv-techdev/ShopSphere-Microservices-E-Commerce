package com.shopsphere.cartservice.exception;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(Long userId, Long productId) {
        super("Product " + productId + " is not in the cart for user " + userId);
    }
}
