package com.shopsphere.notificationservice.entity;

import java.util.Locale;

/** Adding a value? Add a Flyway migration widening notifications_type_check AND a template. */
public enum NotificationType {
    ORDER_PLACED,
    ORDER_CANCELLED,
    PAYMENT_SUCCESSFUL,
    PAYMENT_FAILED,
    LOW_STOCK_ALERT,
    SHIPMENT_DISPATCHED,
    SHIPMENT_DELIVERED,
    EMAIL_VERIFICATION,
    PASSWORD_RESET;

    /** US39 — Thymeleaf template, e.g. SHIPMENT_DISPATCHED → templates/email/shipment-dispatched.html */
    public String templateName() {
        return "email/" + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
