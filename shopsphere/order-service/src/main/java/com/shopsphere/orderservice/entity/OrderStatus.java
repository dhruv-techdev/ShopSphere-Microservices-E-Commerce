package com.shopsphere.orderservice.entity;

public enum OrderStatus {
    /** Order created, awaiting payment. */
    PENDING_PAYMENT,
    /** Payment captured successfully. */
    PAID,
    /** Payment failed; order kept for audit. */
    PAYMENT_FAILED,
    /** Items shipped to the customer. */
    SHIPPED,
    /** Order completed. */
    DELIVERED,
    /** Cancelled by customer or system. */
    CANCELLED
}
