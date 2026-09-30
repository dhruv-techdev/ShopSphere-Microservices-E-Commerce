package com.shopsphere.notificationservice.service;

import com.shopsphere.common.events.OrderCancelledEvent;
import com.shopsphere.notificationservice.entity.Notification;
import com.shopsphere.notificationservice.entity.NotificationType;
import com.shopsphere.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancelledNotificationTest {

    @Mock NotificationRepository notificationRepository;

    @InjectMocks NotificationService notificationService;

    private static OrderCancelledEvent cancelled(String eventId) {
        return OrderCancelledEvent.builder()
                .eventId(eventId).eventType(OrderCancelledEvent.TYPE)
                .orderId(42L).userId(7L)
                .reason("RESERVATION_EXPIRED")
                .reasonDescription("Payment wasn't confirmed in time, so the stock we were holding for this order was released.")
                .cancelledAt(Instant.parse("2026-09-26T22:15:00Z"))
                .totalAmount(new BigDecimal("100.00"))
                .build();
    }

    @Test
    void orderCancelled_notifiesCustomerWithReason() {
        when(notificationRepository.existsBySourceEventId("oc-1")).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.handleOrderCancelled(cancelled("oc-1"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CANCELLED);
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getOrderId()).isEqualTo(42L);
        assertThat(saved.getSourceEventId()).isEqualTo("oc-1");
        assertThat(saved.getSubject()).isEqualTo("Order #42 has been cancelled");
        assertThat(saved.getBody())
                .contains("Payment wasn't confirmed in time")
                .contains("Sep 26, 2026")
                .contains("$100.00");
    }

    @Test
    void orderCancelled_duplicate_isSkipped() {
        when(notificationRepository.existsBySourceEventId("oc-1")).thenReturn(true);

        notificationService.handleOrderCancelled(cancelled("oc-1"));

        verify(notificationRepository, never()).save(any());
    }
}
