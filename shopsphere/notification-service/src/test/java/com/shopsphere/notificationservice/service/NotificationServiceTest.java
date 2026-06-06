package com.shopsphere.notificationservice.service;

import com.shopsphere.common.events.OrderCreatedEvent;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;

    @InjectMocks NotificationService notificationService;

    @Test
    void handleOrderCreated_idempotent_skipsIfAlreadyProcessed() {
        when(notificationRepository.existsBySourceEventId("evt-1")).thenReturn(true);

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId("evt-1").orderId(1L).userId(1L)
                .totalAmount(new BigDecimal("10.00")).itemCount(1)
                .items(List.of()).occurredAt(Instant.now())
                .build();

        notificationService.handleOrderCreated(event);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void handleOrderCreated_persistsNotificationOnce() {
        when(notificationRepository.existsBySourceEventId("evt-2")).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId("evt-2").orderId(7L).userId(1L)
                .totalAmount(new BigDecimal("99.99")).itemCount(2)
                .items(List.of()).occurredAt(Instant.now())
                .build();

        notificationService.handleOrderCreated(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_PLACED);
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getSourceEventId()).isEqualTo("evt-2");
        assertThat(saved.getSubject()).contains("#7");
    }
}
