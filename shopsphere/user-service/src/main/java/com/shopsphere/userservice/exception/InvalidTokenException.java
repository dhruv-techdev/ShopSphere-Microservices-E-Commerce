package com.shopsphere.userservice.exception;

/** Verification / reset token unknown, used or expired. Deliberately non-specific. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("This link is invalid or has expired");
    }
}
