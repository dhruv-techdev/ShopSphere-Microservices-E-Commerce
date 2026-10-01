package com.shopsphere.paymentservice.service;

import com.shopsphere.paymentservice.dto.DuplicateChargeResponse;
import com.shopsphere.paymentservice.dto.PaymentSummaryResponse;
import com.shopsphere.paymentservice.entity.Payment;
import com.shopsphere.paymentservice.entity.PaymentStatus;
import com.shopsphere.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @InjectMocks AdminPaymentService service;

    private static PaymentRepository.StatusTotal total(PaymentStatus status, long count, String amount) {
        return new PaymentRepository.StatusTotal() {
            @Override public PaymentStatus getStatus() { return status; }
            @Override public Long getPaymentCount() { return count; }
            @Override public BigDecimal getAmount() { return new BigDecimal(amount); }
        };
    }

    private static PaymentRepository.DuplicateCapture duplicate(long orderId, long count, String captured) {
        return new PaymentRepository.DuplicateCapture() {
            @Override public Long getOrderId() { return orderId; }
            @Override public Long getUserId() { return 7L; }
            @Override public Long getSuccessfulPayments() { return count; }
            @Override public BigDecimal getTotalCaptured() { return new BigDecimal(captured); }
            @Override public Instant getLastCapturedAt() { return Instant.parse("2026-09-30T10:00:00Z"); }
        };
    }

    private static Payment payment(long orderId, String ref) {
        return Payment.builder().orderId(orderId).userId(7L).paymentReference(ref)
                .amount(new BigDecimal("30.00")).status(PaymentStatus.SUCCESSFUL).build();
    }

    @Test
    void summary_zeroFillsStatuses_andComputesSuccessRate() {
        Instant from = Instant.parse("2026-09-24T00:00:00Z");
        when(paymentRepository.totalsByStatus(eq(from), eq(AdminPaymentService.ALL_TIME_TO)))
                .thenReturn(List.of(total(PaymentStatus.SUCCESSFUL, 9, "900.00"), total(PaymentStatus.FAILED, 1, "50.00")));
        when(paymentRepository.countOrdersCapturedMoreThanOnce(PaymentStatus.SUCCESSFUL)).thenReturn(2L);

        PaymentSummaryResponse summary = service.summary(from, null);

        assertThat(summary.byStatus()).extracting(PaymentSummaryResponse.StatusTotal::status)
                .containsExactly(PaymentStatus.values());
        assertThat(summary.byStatus()).filteredOn(s -> s.status() == PaymentStatus.PENDING)
                .singleElement().satisfies(s -> {
                    assertThat(s.count()).isZero();
                    assertThat(s.amount()).isEqualByComparingTo("0");
                });
        assertThat(summary.totalCount()).isEqualTo(10);
        assertThat(summary.successRate()).isEqualTo(0.9);
        assertThat(summary.duplicateChargeOrders()).isEqualTo(2);
        assertThat(summary.from()).isEqualTo(from);
    }

    @Test
    void summary_withNothingSettled_hasNoSuccessRate_andUsesAllTime() {
        when(paymentRepository.totalsByStatus(AdminPaymentService.ALL_TIME_FROM, AdminPaymentService.ALL_TIME_TO))
                .thenReturn(List.of(total(PaymentStatus.PENDING, 1, "10.00")));

        PaymentSummaryResponse summary = service.summary(null, null);

        assertThat(summary.successRate()).isNull();
        assertThat(summary.totalCount()).isEqualTo(1);
    }

    @Test
    void duplicates_groupReferencesPerOrder() {
        when(paymentRepository.findDuplicateCaptures(eq(PaymentStatus.SUCCESSFUL), any()))
                .thenReturn(List.of(duplicate(50L, 2, "60.00")));
        when(paymentRepository.findByOrderIdInAndStatusOrderByCreatedAtAsc(anyCollection(), eq(PaymentStatus.SUCCESSFUL)))
                .thenReturn(List.of(payment(50L, "ref-a"), payment(50L, "ref-b")));

        List<DuplicateChargeResponse> duplicates = service.duplicates();

        assertThat(duplicates).singleElement().satisfies(d -> {
            assertThat(d.orderId()).isEqualTo(50L);
            assertThat(d.successfulPayments()).isEqualTo(2);
            assertThat(d.totalCaptured()).isEqualByComparingTo("60.00");
            assertThat(d.paymentReferences()).containsExactly("ref-a", "ref-b");
        });
    }

    @Test
    void duplicates_none_skipsReferenceLookup() {
        when(paymentRepository.findDuplicateCaptures(eq(PaymentStatus.SUCCESSFUL), any())).thenReturn(List.of());

        assertThat(service.duplicates()).isEmpty();
        verify(paymentRepository, never()).findByOrderIdInAndStatusOrderByCreatedAtAsc(anyCollection(), any());
    }
}
