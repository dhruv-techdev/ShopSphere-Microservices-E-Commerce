package com.shopsphere.common.events;

/**
 * Centralized topic names. Every producer and consumer references constants here
 * — never hard-code topic names in services. Keeps schema/topic evolution sane.
 * The .NET shipping-service mirrors these in ShippingService.Api.Messaging.Topics.
 */
public final class Topics {

    private Topics() {}

    public static final String ORDER_CREATED                     = "order.created";
    public static final String ORDER_CANCELLED                   = "order.cancelled";
    public static final String PAYMENT_SUCCESSFUL                = "payment.successful";
    public static final String PAYMENT_FAILED                    = "payment.failed";
    public static final String LOW_STOCK                         = "inventory.low-stock";
    public static final String RESERVATION_EXPIRED               = "inventory.reservation-expired";
    public static final String SHIPMENT_DISPATCHED               = "shipment.dispatched";
    public static final String SHIPMENT_DELIVERED                = "shipment.delivered";
    public static final String NOTIFICATION_DLQ                  = "notification.dlq";
    public static final String USER_EMAIL_VERIFICATION_REQUESTED = "user.email-verification-requested";
    public static final String USER_PASSWORD_RESET_REQUESTED     = "user.password-reset-requested";
}
