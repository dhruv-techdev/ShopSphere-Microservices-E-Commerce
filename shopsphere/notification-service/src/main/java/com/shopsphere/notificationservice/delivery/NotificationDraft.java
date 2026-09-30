package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.NotificationType;

import java.util.Map;

/**
 * What an event handler wants to say. {@code body} is the plain-text version (stored + text/plain);
 * {@code model} feeds the HTML template for {@code type}.
 */
public record NotificationDraft(NotificationType type,
                                Long userId,
                                Long orderId,
                                String subject,
                                String body,
                                String sourceEventId,
                                Map<String, Object> model) {
}
