package com.shopsphere.notificationservice.dto;

import com.shopsphere.notificationservice.entity.DeliveryStatus;
import com.shopsphere.notificationservice.entity.Notification;
import com.shopsphere.notificationservice.entity.NotificationType;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * US46 — full notification record for the admin log. Bodies of emails that carry one-time
 * links (verification / password reset) are redacted so admins never see live tokens.
 */
public record AdminNotificationResponse(
        Long id,
        NotificationType type,
        Long userId,
        Long orderId,
        String recipient,
        String channel,
        String subject,
        String body,
        boolean bodyRedacted,
        DeliveryStatus deliveryStatus,
        int attempts,
        String providerMessageId,
        String failureReason,
        Instant sentAt,
        Instant lastAttemptAt,
        Instant nextAttemptAt,
        Instant deadLetteredAt,
        Instant createdAt) {

    public static final Set<NotificationType> ONE_TIME_LINK_TYPES =
            EnumSet.of(NotificationType.EMAIL_VERIFICATION, NotificationType.PASSWORD_RESET);

    public static AdminNotificationResponse from(Notification n) {
        boolean redact = ONE_TIME_LINK_TYPES.contains(n.getType());
        return new AdminNotificationResponse(
                n.getId(), n.getType(), n.getUserId(), n.getOrderId(), n.getRecipient(), n.getChannel(),
                n.getSubject(), redact ? null : n.getBody(), redact, n.getDeliveryStatus(),
                n.getAttempts() == null ? 0 : n.getAttempts(), n.getProviderMessageId(), n.getFailureReason(),
                n.getSentAt(), n.getLastAttemptAt(), n.getNextAttemptAt(), n.getDeadLetteredAt(), n.getCreatedAt());
    }
}
