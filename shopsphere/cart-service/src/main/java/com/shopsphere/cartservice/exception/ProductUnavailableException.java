package com.shopsphere.cartservice.exception;

public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(Long productId) {
        super("Product is not available for purchase: " + productId);
    }
}
