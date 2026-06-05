package com.shopsphere.cartservice.exception;

public class ProductLookupException extends RuntimeException {
    public ProductLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
