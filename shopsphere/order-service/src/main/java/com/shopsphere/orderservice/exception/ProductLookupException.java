package com.shopsphere.orderservice.exception;

public class ProductLookupException extends RuntimeException {
    public ProductLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
