package com.shopsphere.notificationservice.delivery;

/** A rendered message ready for a channel. */
public record OutboundMessage(String to, String subject, String textBody, String htmlBody) {
}
