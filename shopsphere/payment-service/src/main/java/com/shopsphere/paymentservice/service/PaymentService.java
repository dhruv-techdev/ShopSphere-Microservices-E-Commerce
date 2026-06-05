package com.shopsphere.paymentservice.service;

import com.shopsphere.paymentservice.dto.PaymentRequest;
import com.shopsphere.paymentservice.dto.PaymentResponse;
import com.shopsphere.paymentservice.entity.Payment;
import com.shopsphere.paymentservice.entity.PaymentStatus;
import com.shopsphere.paymentservice.exception.PaymentNotFoundException;
import com.shopsphere.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Value("${app.payment.success-rate}")
    private double successRate;

    @Value("${app.payment.simulated-latency-ms}")
    private long simulatedLatencyMs;

    /* ---------------------------------------------------------------- */
    /* ST4 + ST5 + ST6 + ST7 — Simulate                                  */
    /* ---------------------------------------------------------------- */

    @Transactional
    public PaymentResponse simulate(PaymentRequest request) {
        // ST4: Always start by persisting in PENDING so we have an audit record
        // even if something crashes mid-simulation.
        Payment payment = Payment.builder()
                .paymentReference(UUID.randomUUID().toString())
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);
        log.debug("Recorded pending payment {} for order {} amount {}",
                payment.getPaymentReference(), payment.getOrderId(), payment.getAmount());

        // Simulate gateway latency
        if (simulatedLatencyMs > 0) {
            try {
                Thread.sleep(simulatedLatencyMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        // ST5 + ST6: decide outcome
        boolean succeeded = switch (request.getMode() == null
                ? PaymentRequest.SimulationMode.RANDOM
                : request.getMode()) {
            case ALWAYS_SUCCEED -> true;
            case ALWAYS_FAIL -> false;
            case RANDOM -> ThreadLocalRandom.current().nextDouble() < successRate;
        };

        // ST7: update status with reason
        if (succeeded) {
            payment.setStatus(PaymentStatus.SUCCESSFUL);
            payment.setMessage("Payment captured (simulated)");
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setMessage(simulateFailureReason());
        }

        payment = paymentRepository.save(payment);
        log.info("Payment {} for order {} -> {}",
                payment.getPaymentReference(), payment.getOrderId(), payment.getStatus());

        return toResponse(payment);
    }

    /* ---------------------------------------------------------------- */
    /* Read APIs                                                         */
    /* ---------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public PaymentResponse getByReference(String reference) {
        Payment payment = paymentRepository.findByPaymentReference(reference)
                .orElseThrow(() -> new PaymentNotFoundException(reference));
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getByOrderId(Long orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(this::toResponse)
                .toList();
    }

    public long countPayments() {
        return paymentRepository.count();
    }

    /* ---------------------------------------------------------------- */
    /* Helpers                                                           */
    /* ---------------------------------------------------------------- */

    private String simulateFailureReason() {
        String[] reasons = {
                "Card declined by issuer",
                "Insufficient funds",
                "Card expired",
                "Issuer unavailable",
                "Suspected fraud"
        };
        return reasons[ThreadLocalRandom.current().nextInt(reasons.length)] + " (simulated)";
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .paymentReference(p.getPaymentReference())
                .orderId(p.getOrderId())
                .userId(p.getUserId())
                .amount(p.getAmount())
                .status(p.getStatus())
                .message(p.getMessage())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
