package com.shopsphere.notificationservice.service;

import com.shopsphere.notificationservice.dto.AdminNotificationResponse;
import com.shopsphere.notificationservice.dto.NotificationSummaryResponse;
import com.shopsphere.notificationservice.entity.DeliveryStatus;
import com.shopsphere.notificationservice.entity.Notification;
import com.shopsphere.notificationservice.entity.NotificationType;
import com.shopsphere.notificationservice.exception.NotificationNotFoundException;
import com.shopsphere.notificationservice.exception.NotificationRetryNotAllowedException;
import com.shopsphere.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock NotificationRepository repository;
    AdminNotificationService service;

    @BeforeEach
    void setUp() {
        service = new AdminNotificationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Notification notification(NotificationType type, DeliveryStatus status) {
        return Notification.builder().id(15L).type(type).userId(7L).subject("Subject")
                .body("Body with https://shop/reset?token=abc").sourceEventId("evt-1")
                .deliveryStatus(status).attempts(5).failureReason("SMTP 550").build();
    }

    private static NotificationRepository.StatusTotal total(DeliveryStatus status, long count) {
        return new NotificationRepository.StatusTotal() {
            @Override public DeliveryStatus getStatus() { return status; }
            @Override public Long getTotal() { return count; }
        };
    }

    @Test
    void response_redactsOneTimeLinkBodies() {
        AdminNotificationResponse reset = AdminNotificationResponse.from(
                notification(NotificationType.PASSWORD_RESET, DeliveryStatus.SENT));
        AdminNotificationResponse order = AdminNotificationResponse.from(
                notification(NotificationType.ORDER_PLACED, DeliveryStatus.SENT));

        assertThat(reset.body()).isNull();
        assertThat(reset.bodyRedacted()).isTrue();
        assertThat(order.body()).contains("Body");
        assertThat(order.bodyRedacted()).isFalse();
    }

    @Test
    void retry_failedNotification_requeuesItDueNow() {
        Notification failed = notification(NotificationType.ORDER_PLACED, DeliveryStatus.FAILED);
        Notification requeued = notification(NotificationType.ORDER_PLACED, DeliveryStatus.RETRYING);
        requeued.setNextAttemptAt(NOW);
        when(repository.findById(15L)).thenReturn(Optional.of(failed), Optional.of(requeued));
        when(repository.requeue(15L, DeliveryStatus.FAILED, DeliveryStatus.RETRYING, NOW)).thenReturn(1);

        AdminNotificationResponse result = service.retry(15L);

        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.RETRYING);
        assertThat(result.nextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void retry_sentNotification_isRefused() {
        when(repository.findById(15L)).thenReturn(Optional.of(notification(NotificationType.ORDER_PLACED, DeliveryStatus.SENT)));

        assertThatThrownBy(() -> service.retry(15L))
                .isInstanceOf(NotificationRetryNotAllowedException.class)
                .hasMessageContaining("only FAILED");
        verify(repository, never()).requeue(anyLong(), any(), any(), any());
    }

    @Test
    void retry_oneTimeLinkEmail_isRefused() {
        when(repository.findById(15L)).thenReturn(Optional.of(notification(NotificationType.EMAIL_VERIFICATION, DeliveryStatus.FAILED)));

        assertThatThrownBy(() -> service.retry(15L))
                .isInstanceOf(NotificationRetryNotAllowedException.class)
                .hasMessageContaining("one-time link");
    }

    @Test
    void retry_lostRace_isRefused() {
        when(repository.findById(15L)).thenReturn(Optional.of(notification(NotificationType.ORDER_PLACED, DeliveryStatus.FAILED)));
        when(repository.requeue(15L, DeliveryStatus.FAILED, DeliveryStatus.RETRYING, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.retry(15L)).isInstanceOf(NotificationRetryNotAllowedException.class);
    }

    @Test
    void get_unknown_throwsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void summary_zeroFillsEveryStatus() {
        when(repository.statusTotals()).thenReturn(List.of(total(DeliveryStatus.SENT, 10), total(DeliveryStatus.FAILED, 2)));

        NotificationSummaryResponse summary = service.summary();

        assertThat(summary.total()).isEqualTo(12);
        assertThat(summary.byStatus()).containsKeys(DeliveryStatus.values());
        assertThat(summary.byStatus().get(DeliveryStatus.SKIPPED)).isZero();
        assertThat(summary.byStatus().get(DeliveryStatus.FAILED)).isEqualTo(2);
    }
}
