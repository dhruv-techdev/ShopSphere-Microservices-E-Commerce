package com.shopsphere.paymentservice.controller;

import com.shopsphere.paymentservice.dto.DuplicateChargeResponse;
import com.shopsphere.paymentservice.dto.PaymentResponse;
import com.shopsphere.paymentservice.dto.PaymentSummaryResponse;
import com.shopsphere.paymentservice.entity.PaymentStatus;
import com.shopsphere.paymentservice.service.AdminPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** US46 — payment reconciliation for the admin console (ADMIN only, here and at the gateway). */
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Payments", description = "Payment listing and reconciliation (ADMIN)")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    @GetMapping
    @Operation(summary = "List payments", description = "Newest first. `from`/`to` are ISO-8601 instants.")
    public Page<PaymentResponse> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long userId,
            @Parameter(example = "2026-09-24T00:00:00Z") @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return adminPaymentService.search(status, orderId, userId, from, to, pageable);
    }

    @GetMapping("/summary")
    @Operation(summary = "Counts and amounts per status, success rate, orders captured twice")
    public PaymentSummaryResponse summary(@RequestParam(required = false) Instant from,
                                          @RequestParam(required = false) Instant to) {
        return adminPaymentService.summary(from, to);
    }

    @GetMapping("/duplicates")
    @Operation(summary = "Orders with more than one SUCCESSFUL payment")
    public List<DuplicateChargeResponse> duplicates() {
        return adminPaymentService.duplicates();
    }
}
