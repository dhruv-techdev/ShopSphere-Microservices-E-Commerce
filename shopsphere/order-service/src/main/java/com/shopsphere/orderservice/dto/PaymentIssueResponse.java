package com.shopsphere.orderservice.dto;

import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** US46 — an order whose status and payment disagree. */
public record PaymentIssueResponse(
        Long orderId,
        Long userId,
        OrderStatus orderStatus,
        Issue issue,
        BigDecimal totalAmount,
        String paymentReference,
        Instant since) {

    public enum Issue {
        /** CANCELLED, but payment.successful was recorded. */
        REFUND_REQUIRED,
        /** PAYMENT_FAILED, but a later capture succeeded. */
        PAID_AFTER_FAILURE,
        /** Still PENDING_PAYMENT long after it was placed. */
        PAYMENT_OVERDUE
    }

    public static PaymentIssueResponse of(Order o, Issue issue, Instant since) {
        return new PaymentIssueResponse(o.getId(), o.getUserId(), o.getStatus(), issue, o.getTotalAmount(),
                o.getPaymentReference(), since);
    }
}
