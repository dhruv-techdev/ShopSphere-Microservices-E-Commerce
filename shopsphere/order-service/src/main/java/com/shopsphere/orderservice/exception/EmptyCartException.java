package com.shopsphere.orderservice.exception;

public class EmptyCartException extends RuntimeException {
    public EmptyCartException(Long userId) {
        super("Cannot create an order: cart is empty for user " + userId);
    }
}
