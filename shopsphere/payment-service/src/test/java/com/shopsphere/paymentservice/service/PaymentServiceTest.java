package com.shopsphere.paymentservice.service;

import com.shopsphere.paymentservice.dto.PaymentRequest;
import com.shopsphere.paymentservice.dto.PaymentResponse;
import com.shopsphere.paymentservice.entity.Payment;
import com.shopsphere.paymentservice.entity.PaymentStatus;
import com.shopsphere.paymentservice.messaging.PaymentEventPublisher;
import com.shopsphere.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void simulate_alwaysSucceed_returnsSuccessful() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentRequest req = PaymentRequest.builder()
                .orderId(1L).userId(1L).amount(new BigDecimal("10.00"))
                .mode(PaymentRequest.SimulationMode.ALWAYS_SUCCEED)
                .build();

        PaymentResponse response = paymentService.simulate(req);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);
    }

    @Test
    void simulate_alwaysFail_returnsFailed() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentRequest req = PaymentRequest.builder()
                .orderId(1L).userId(1L).amount(new BigDecimal("10.00"))
                .mode(PaymentRequest.SimulationMode.ALWAYS_FAIL)
                .build();

        PaymentResponse response = paymentService.simulate(req);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getMessage()).contains("simulated");
    }
}
