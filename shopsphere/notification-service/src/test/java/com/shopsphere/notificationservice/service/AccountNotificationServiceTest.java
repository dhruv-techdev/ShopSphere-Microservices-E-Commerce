package com.shopsphere.notificationservice.service;

import com.shopsphere.common.events.UserEmailVerificationRequestedEvent;
import com.shopsphere.common.events.UserPasswordResetRequestedEvent;
import com.shopsphere.notificationservice.delivery.NotificationDispatcher;
import com.shopsphere.notificationservice.delivery.NotificationDraft;
import com.shopsphere.notificationservice.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountNotificationServiceTest {

    @Mock NotificationDispatcher dispatcher;

    @InjectMocks AccountNotificationService service;

    private NotificationDraft captured() {
        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(dispatcher).deliver(captor.capture());
        return captor.getValue();
    }

    @Test
    void verificationRequested_buildsEmailVerificationDraftWithLink() {
        service.handleEmailVerificationRequested(UserEmailVerificationRequestedEvent.builder()
                .eventId("ev-1").userId(7L).firstName("Jane")
                .verificationUrl("https://shop.test/verify-email?token=abc")
                .expiresAt(Instant.parse("2026-09-30T12:00:00Z"))
                .build());

        NotificationDraft draft = captured();
        assertThat(draft.type()).isEqualTo(NotificationType.EMAIL_VERIFICATION);
        assertThat(draft.userId()).isEqualTo(7L);
        assertThat(draft.sourceEventId()).isEqualTo("ev-1");
        assertThat(draft.body()).contains("Hi Jane").contains("https://shop.test/verify-email?token=abc");
        assertThat(draft.model())
                .containsEntry("actionUrl", "https://shop.test/verify-email?token=abc")
                .containsEntry("expiresAt", "Sep 30, 2026 at 12:00 UTC");
    }

    @Test
    void resetRequested_buildsPasswordResetDraftWithLink() {
        service.handlePasswordResetRequested(UserPasswordResetRequestedEvent.builder()
                .eventId("pr-1").userId(7L).firstName(null)
                .resetUrl("https://shop.test/reset-password?token=xyz")
                .expiresAt(Instant.parse("2026-09-29T12:30:00Z"))
                .build());

        NotificationDraft draft = captured();
        assertThat(draft.type()).isEqualTo(NotificationType.PASSWORD_RESET);
        assertThat(draft.subject()).isEqualTo("Reset your ShopSphere password");
        assertThat(draft.body()).contains("Hi there").contains("reset-password?token=xyz");
        assertThat(draft.model()).containsEntry("actionUrl", "https://shop.test/reset-password?token=xyz");
    }
}
