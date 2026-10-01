package com.shopsphere.e2e.pages;

/** Order status as labelled by the admin app (order-status.ts). */
public enum OrderStatus implements Labelled {
    PENDING_PAYMENT("Awaiting payment"),
    PAID("Paid"),
    PAYMENT_FAILED("Payment failed"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    /** States an order never leaves on the happy path; waiting for anything else is pointless. */
    public boolean isDeadEnd() {
        return this == PAYMENT_FAILED || this == DELIVERED || this == CANCELLED;
    }

    public static OrderStatus fromLabel(String label) {
        return Labelled.fromLabel(OrderStatus.class, label);
    }
}
