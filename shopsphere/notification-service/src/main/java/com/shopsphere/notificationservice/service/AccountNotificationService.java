package com.shopsphere.notificationservice.service;

import com.shopsphere.common.events.UserEmailVerificationRequestedEvent;
import com.shopsphere.common.events.UserPasswordResetRequestedEvent;
import com.shopsphere.notificationservice.delivery.NotificationDispatcher;
import com.shopsphere.notificationservice.delivery.NotificationDraft;
import com.shopsphere.notificationservice.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * US41 — account emails requested by user-service. Delivered (with retries / DLQ) through the
 * same pipeline as order notifications. The links are single-use and short-lived.
 */
@Service
@RequiredArgsConstructor
public class AccountNotificationService {

    private final NotificationDispatcher dispatcher;

    public void handleEmailVerificationRequested(UserEmailVerificationRequestedEvent event) {
        String expires = NotificationService.when(event.getExpiresAt());
        String subject = "Confirm your ShopSphere email address";
        String body = String.format(
                "Hi %s, thanks for signing up! Confirm your email address by opening this link: %s " +
                        "(expires on %s). If you didn't create a ShopSphere account, you can ignore this email.",
                name(event.getFirstName()), event.getVerificationUrl(), expires);

        dispatcher.deliver(new NotificationDraft(NotificationType.EMAIL_VERIFICATION, event.getUserId(),
                null, subject, body, event.getEventId(),
                model("actionUrl", event.getVerificationUrl(), "expiresAt", expires)));
    }

    public void handlePasswordResetRequested(UserPasswordResetRequestedEvent event) {
        String expires = NotificationService.when(event.getExpiresAt());
        String subject = "Reset your ShopSphere password";
        String body = String.format(
                "Hi %s, we received a request to reset your password. Choose a new one here: %s " +
                        "(single use, expires on %s). Didn't ask for this? Ignore this email — your password won't change.",
                name(event.getFirstName()), event.getResetUrl(), expires);

        dispatcher.deliver(new NotificationDraft(NotificationType.PASSWORD_RESET, event.getUserId(),
                null, subject, body, event.getEventId(),
                model("actionUrl", event.getResetUrl(), "expiresAt", expires)));
    }

    private static String name(String firstName) {
        return firstName == null || firstName.isBlank() ? "there" : firstName;
    }

    private static Map<String, Object> model(Object... keyValues) {
        Map<String, Object> model = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            model.put((String) keyValues[i], keyValues[i + 1]);
        }
        return model;
    }
}
