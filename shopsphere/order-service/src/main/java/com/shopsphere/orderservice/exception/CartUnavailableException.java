package com.shopsphere.orderservice.exception;

public class CartUnavailableException extends RuntimeException {
    public CartUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
