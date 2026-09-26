package com.shopsphere.paymentservice.service;

import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.paymentservice.dto.PaymentRequest;
import com.shopsphere.paymentservice.dto.PaymentResponse;
import com.shopsphere.paymentservice.entity.Payment;
import com.shopsphere.paymentservice.entity.PaymentStatus;
import com.shopsphere.paymentservice.messaging.PaymentEventPublisher;
import com.shopsphere.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentEventPublisher paymentEventPublisher;
    @Mock ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks PaymentService paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "successRate", 0.85);
        ReflectionTestUtils.setField(paymentService, "simulatedLatencyMs", 0L);
    }

    private void stubSave() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
    }

    @Test
    void simulate_alwaysSucceed_returnsSuccessful() {
        stubSave();

        PaymentRequest req = PaymentRequest.builder()
                .orderId(1L).userId(1L).amount(new BigDecimal("10.00"))
                .mode(PaymentRequest.SimulationMode.ALWAYS_SUCCEED)
                .build();

        PaymentResponse response = paymentService.simulate(req);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);
    }

    @Test
    void simulate_alwaysFail_returnsFailed_andPublishesEventWithIdAndTimestamp() {
        stubSave();

        PaymentRequest req = PaymentRequest.builder()
                .orderId(1L).userId(1L).amount(new BigDecimal("10.00"))
                .mode(PaymentRequest.SimulationMode.ALWAYS_FAIL)
                .build();

        PaymentResponse response = paymentService.simulate(req);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getMessage()).contains("simulated");

        ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(paymentEventPublisher).publishFailed(captor.capture());
        assertThat(captor.getValue().getEventId()).isNotBlank();
        assertThat(captor.getValue().getOccurredAt()).isNotNull();
        assertThat(captor.getValue().getEventType()).isEqualTo(PaymentFailedEvent.TYPE);
    }

    @Test
    void processOrderCreated_success_forwardsShippingAddressAndSetsEventId() {
        stubSave();
        ReflectionTestUtils.setField(paymentService, "successRate", 1.0);

        OrderCreatedEvent.ShippingAddress address = OrderCreatedEvent.ShippingAddress.builder()
                .recipientName("Jane Doe")
                .line1("123 King St W")
                .city("Toronto")
                .state("ON")
                .postalCode("M5V 3L9")
                .country("CA")
                .build();

        OrderCreatedEvent orderCreated = OrderCreatedEvent.builder()
                .eventId("order-evt-1")
                .eventType(OrderCreatedEvent.TYPE)
                .orderId(42L)
                .userId(7L)
                .totalAmount(new BigDecimal("100.00"))
                .itemCount(2)
                .items(List.of(OrderCreatedEvent.Item.builder()
                        .productId(10L).productName("Keyboard")
                        .unitPrice(new BigDecimal("50.00")).quantity(2)
                        .build()))
                .shippingAddress(address)
                .build();

        paymentService.processOrderCreated(orderCreated);

        ArgumentCaptor<PaymentSuccessfulEvent> captor = ArgumentCaptor.forClass(PaymentSuccessfulEvent.class);
        verify(paymentEventPublisher).publishSuccessful(captor.capture());
        PaymentSuccessfulEvent published = captor.getValue();

        assertThat(published.getEventId()).isNotBlank().isNotEqualTo("order-evt-1");
        assertThat(published.getOccurredAt()).isNotNull();
        assertThat(published.getEventType()).isEqualTo(PaymentSuccessfulEvent.TYPE);
        assertThat(published.getOrderId()).isEqualTo(42L);
        assertThat(published.getItems()).hasSize(1);
        assertThat(published.getShippingAddress()).isNotNull();
        assertThat(published.getShippingAddress().getCity()).isEqualTo("Toronto");
        assertThat(published.getShippingAddress().getCountry()).isEqualTo("CA");
    }
}
