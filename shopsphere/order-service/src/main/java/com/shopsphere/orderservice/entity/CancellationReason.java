package com.shopsphere.orderservice.entity;

/** Why an order was cancelled. Stored as its name; add values freely (no DB CHECK). */
public enum CancellationReason {

    RESERVATION_EXPIRED("Payment wasn't confirmed in time, so the stock we were holding for this order was released."),

    /** US42 — cancelled by an administrator via /api/v1/admin/orders/{id}/cancel. */
    ADMIN_CANCELLED("Our support team cancelled this order. Please contact us if you have any questions.");

    private final String description;

    CancellationReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
