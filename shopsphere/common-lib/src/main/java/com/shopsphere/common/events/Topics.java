package com.shopsphere.common.events;

/**
 * Centralized topic names. Every producer and consumer references constants here
 * — never hard-code topic names in services. Keeps schema/topic evolution sane.
 */
public final class Topics {

    private Topics() {}

    public static final String ORDER_CREATED       = "order.created";
    public static final String PAYMENT_SUCCESSFUL  = "payment.successful";
    public static final String PAYMENT_FAILED      = "payment.failed";
    public static final String LOW_STOCK           = "inventory.low-stock";
}
