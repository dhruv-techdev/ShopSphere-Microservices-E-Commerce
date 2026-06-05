package com.shopsphere.orderservice.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productName, int requested, int available) {
        super("Insufficient stock for '" + productName + "': requested " + requested
                + ", available " + available);
    }
}
