package com.shopsphere.userservice.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Please verify your email address before logging in");
    }
}
