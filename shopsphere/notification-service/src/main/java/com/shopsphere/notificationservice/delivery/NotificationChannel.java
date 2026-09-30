package com.shopsphere.notificationservice.delivery;

/**
 * US39 — one implementation is active per environment, chosen by app.notification.channel.
 * Implementations must not throw: report problems as {@link DeliveryResult#failed}.
 */
public interface NotificationChannel {

    /** Short id stored on the notification: log | smtp | sendgrid. */
    String name();

    DeliveryResult send(OutboundMessage message);
}
