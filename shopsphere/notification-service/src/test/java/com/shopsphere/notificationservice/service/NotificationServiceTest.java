package com.shopsphere.notificationservice.service;

import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.common.events.ShipmentDeliveredEvent;
import com.shopsphere.common.events.ShipmentDispatchedEvent;
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

    @Test
    void handleShipmentDispatched_notifiesCustomerWithTracking() {
        when(notificationRepository.existsBySourceEventId("ship-1")).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        ShipmentDispatchedEvent event = ShipmentDispatchedEvent.builder()
                .eventId("ship-1").eventType(ShipmentDispatchedEvent.TYPE)
                .shipmentId(5L).orderId(42L).userId(7L)
                .carrier("ShopSphere Express").trackingNumber("SSX2609264K7QZ9M2PA")
                .shippedAt(Instant.parse("2026-09-26T12:00:00Z"))
                .shippingAddress(OrderCreatedEvent.ShippingAddress.builder()
                        .city("Toronto").country("CA").build())
                .build();

        notificationService.handleShipmentDispatched(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.SHIPMENT_DISPATCHED);
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getOrderId()).isEqualTo(42L);
        assertThat(saved.getSourceEventId()).isEqualTo("ship-1");
        assertThat(saved.getSubject()).isEqualTo("Order #42 has shipped");
        assertThat(saved.getBody())
                .contains("SSX2609264K7QZ9M2PA")
                .contains("ShopSphere Express")
                .contains("Toronto, CA")
                .contains("Sep 26, 2026");
    }

    @Test
    void handleShipmentDelivered_notifiesCustomer() {
        when(notificationRepository.existsBySourceEventId("del-1")).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        ShipmentDeliveredEvent event = ShipmentDeliveredEvent.builder()
                .eventId("del-1").eventType(ShipmentDeliveredEvent.TYPE)
                .shipmentId(5L).orderId(42L).userId(7L)
                .trackingNumber("SSX2609264K7QZ9M2PA")
                .deliveredAt(Instant.parse("2026-09-28T15:30:00Z"))
                .build();

        notificationService.handleShipmentDelivered(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.SHIPMENT_DELIVERED);
        assertThat(captor.getValue().getSubject()).isEqualTo("Order #42 has been delivered");
        assertThat(captor.getValue().getBody()).contains("Sep 28, 2026").contains("SSX2609264K7QZ9M2PA");
    }

    @Test
    void handleShipmentDelivered_duplicate_isSkipped() {
        when(notificationRepository.existsBySourceEventId("del-1")).thenReturn(true);

        notificationService.handleShipmentDelivered(ShipmentDeliveredEvent.builder()
                .eventId("del-1").orderId(42L).userId(7L).build());

        verify(notificationRepository, never()).save(any());
    }
}
