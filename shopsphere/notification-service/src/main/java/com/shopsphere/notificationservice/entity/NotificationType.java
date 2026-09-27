package com.shopsphere.notificationservice.entity;

/** Adding a value? Add a Flyway migration widening notifications_type_check too. */
public enum NotificationType {
    ORDER_PLACED,
    ORDER_CANCELLED,
    PAYMENT_SUCCESSFUL,
    PAYMENT_FAILED,
    LOW_STOCK_ALERT,
    SHIPMENT_DISPATCHED,
    SHIPMENT_DELIVERED
}
