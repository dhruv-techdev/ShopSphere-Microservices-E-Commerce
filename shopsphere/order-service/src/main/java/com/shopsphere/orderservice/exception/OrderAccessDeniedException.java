package com.shopsphere.orderservice.exception;

public class OrderAccessDeniedException extends RuntimeException {
    public OrderAccessDeniedException(Long orderId) {
        super("You are not authorized to view order " + orderId);
    }
}
