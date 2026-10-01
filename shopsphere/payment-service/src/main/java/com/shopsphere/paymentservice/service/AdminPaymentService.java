package com.shopsphere.paymentservice.service;

import com.shopsphere.paymentservice.dto.DuplicateChargeResponse;
import com.shopsphere.paymentservice.dto.PaymentResponse;
import com.shopsphere.paymentservice.dto.PaymentSummaryResponse;
import com.shopsphere.paymentservice.entity.Payment;
import com.shopsphere.paymentservice.entity.PaymentStatus;
import com.shopsphere.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** US46 — read models for the admin payment reconciliation view. */
@Service
@RequiredArgsConstructor
public class AdminPaymentService {

    static final int MAX_DUPLICATES = 200;
    static final Instant ALL_TIME_FROM = Instant.EPOCH;
    static final Instant ALL_TIME_TO = Instant.parse("9999-12-31T00:00:00Z");

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public Page<PaymentResponse> search(PaymentStatus status, Long orderId, Long userId,
                                        Instant from, Instant to, Pageable pageable) {
        Specification<Payment> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (orderId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("orderId"), orderId));
        }
        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.<Instant>get("createdAt"), to));
        }
        return paymentRepository.findAll(spec, pageable).map(AdminPaymentService::toResponse);
    }

    @Transactional(readOnly = true)
    public PaymentSummaryResponse summary(Instant from, Instant to) {
        Map<PaymentStatus, PaymentRepository.StatusTotal> totals = new EnumMap<>(PaymentStatus.class);
        paymentRepository.totalsByStatus(from != null ? from : ALL_TIME_FROM, to != null ? to : ALL_TIME_TO)
                .forEach(t -> totals.put(t.getStatus(), t));

        List<PaymentSummaryResponse.StatusTotal> rows = Arrays.stream(PaymentStatus.values())
                .map(s -> {
                    PaymentRepository.StatusTotal t = totals.get(s);
                    return new PaymentSummaryResponse.StatusTotal(s,
                            t == null || t.getPaymentCount() == null ? 0 : t.getPaymentCount(),
                            t == null || t.getAmount() == null ? BigDecimal.ZERO : t.getAmount());
                })
                .toList();

        long total = rows.stream().mapToLong(PaymentSummaryResponse.StatusTotal::count).sum();
        long successful = count(rows, PaymentStatus.SUCCESSFUL);
        long failed = count(rows, PaymentStatus.FAILED);
        Double successRate = successful + failed == 0 ? null : (double) successful / (successful + failed);

        return new PaymentSummaryResponse(from, to, total, rows, successRate,
                paymentRepository.countOrdersCapturedMoreThanOnce(PaymentStatus.SUCCESSFUL));
    }

    @Transactional(readOnly = true)
    public List<DuplicateChargeResponse> duplicates() {
        List<PaymentRepository.DuplicateCapture> rows = paymentRepository.findDuplicateCaptures(
                PaymentStatus.SUCCESSFUL, PageRequest.of(0, MAX_DUPLICATES));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> references = paymentRepository
                .findByOrderIdInAndStatusOrderByCreatedAtAsc(
                        rows.stream().map(PaymentRepository.DuplicateCapture::getOrderId).toList(),
                        PaymentStatus.SUCCESSFUL)
                .stream()
                .collect(Collectors.groupingBy(Payment::getOrderId,
                        Collectors.mapping(Payment::getPaymentReference, Collectors.toList())));

        return rows.stream()
                .map(r -> new DuplicateChargeResponse(
                        r.getOrderId(), r.getUserId(), r.getSuccessfulPayments(), r.getTotalCaptured(),
                        references.getOrDefault(r.getOrderId(), List.of()), r.getLastCapturedAt()))
                .toList();
    }

    static PaymentResponse toResponse(Payment p) {
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

    private static long count(List<PaymentSummaryResponse.StatusTotal> rows, PaymentStatus status) {
        return rows.stream().filter(r -> r.status() == status).mapToLong(PaymentSummaryResponse.StatusTotal::count).sum();
    }
}
