package com.shopsphere.paymentservice.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String reference) {
        super("Payment not found: " + reference);
    }
}
