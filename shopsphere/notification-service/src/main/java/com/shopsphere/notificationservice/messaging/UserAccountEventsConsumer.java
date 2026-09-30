package com.shopsphere.notificationservice.messaging;

import com.shopsphere.common.events.Topics;
import com.shopsphere.common.events.UserEmailVerificationRequestedEvent;
import com.shopsphere.common.events.UserPasswordResetRequestedEvent;
import com.shopsphere.notificationservice.service.AccountNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** US41 — never logs the payload: it contains single-use links. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserAccountEventsConsumer {

    private final AccountNotificationService accountNotificationService;

    @KafkaListener(
            topics = Topics.USER_EMAIL_VERIFICATION_REQUESTED,
            groupId = "notification-service",
            containerFactory = "emailVerificationRequestedListenerContainerFactory"
    )
    public void onEmailVerificationRequested(
            @Payload UserEmailVerificationRequestedEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Notification received {} eventId={} userId={} offset={}",
                Topics.USER_EMAIL_VERIFICATION_REQUESTED, event.getEventId(), event.getUserId(), offset);
        accountNotificationService.handleEmailVerificationRequested(event);
        ack.acknowledge();
    }

    @KafkaListener(
            topics = Topics.USER_PASSWORD_RESET_REQUESTED,
            groupId = "notification-service",
            containerFactory = "passwordResetRequestedListenerContainerFactory"
    )
    public void onPasswordResetRequested(
            @Payload UserPasswordResetRequestedEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Notification received {} eventId={} userId={} offset={}",
                Topics.USER_PASSWORD_RESET_REQUESTED, event.getEventId(), event.getUserId(), offset);
        accountNotificationService.handlePasswordResetRequested(event);
        ack.acknowledge();
    }
}
