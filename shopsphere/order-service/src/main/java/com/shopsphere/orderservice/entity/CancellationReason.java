package com.shopsphere.orderservice.entity;

/** Why an order was cancelled. Stored as its name; add values freely (no DB CHECK). */
public enum CancellationReason {

    RESERVATION_EXPIRED("Payment wasn't confirmed in time, so the stock we were holding for this order was released.");

    private final String description;

    CancellationReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
