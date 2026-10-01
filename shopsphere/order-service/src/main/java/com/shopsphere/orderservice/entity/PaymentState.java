package com.shopsphere.orderservice.entity;

/** US45 — payment outcome as seen by order-service (derived, not stored). */
public enum PaymentState {
    PENDING,
    PAID,
    FAILED
}
