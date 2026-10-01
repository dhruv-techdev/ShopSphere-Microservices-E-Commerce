package com.shopsphere.orderservice.controller;

import com.shopsphere.orderservice.dto.AdminOrderSummaryResponse;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.PaymentIssueResponse;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.exception.ApiError;
import com.shopsphere.orderservice.service.AdminOrderService;
import com.shopsphere.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** US42 — ADMIN-only order management (every endpoint, enforced here and at the gateway). */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Orders", description = "Order management across all customers (ADMIN)")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required")
})
public class AdminOrderController {

    private static final int MAX_OVERDUE_MINUTES = 7 * 24 * 60;

    private final AdminOrderService adminOrderService;

    @GetMapping
    @Operation(summary = "List orders", description = "All customers' orders, newest first. Optional status/userId filters.")
    public Page<AdminOrderSummaryResponse> list(
            @Parameter(description = "Filter by status") @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Filter by customer id") @RequestParam(required = false) Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return adminOrderService.search(status, userId, pageable);
    }

    /** US46 — declared before /{orderId}; the literal path wins either way. */
    @GetMapping("/reconciliation")
    @Operation(summary = "Orders whose status and payment disagree",
            description = "Cancelled-but-paid, failed-but-later-paid, and orders awaiting payment for too long.")
    public List<PaymentIssueResponse> reconciliation(
            @Parameter(description = "Minutes after which PENDING_PAYMENT counts as overdue (1–10080)")
            @RequestParam(defaultValue = "30") int pendingOlderThanMinutes) {
        int minutes = Math.max(1, Math.min(pendingOlderThanMinutes, MAX_OVERDUE_MINUTES));
        return adminOrderService.reconciliation(Duration.ofMinutes(minutes));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get any order with its line items")
    @ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public OrderResponse get(@PathVariable @Positive Long orderId) {
        return adminOrderService.get(orderId);
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an order",
            description = """
                    Allowed while the order is PENDING_PAYMENT or PAID. Idempotent for already-cancelled orders.
                    Publishes order.cancelled (reason ADMIN_CANCELLED) so the customer is notified.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled (or already cancelled)"),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Order already shipped/delivered/failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public OrderResponse cancel(@PathVariable @Positive Long orderId,
                                @AuthenticationPrincipal AuthenticatedUser admin) {
        return adminOrderService.cancel(orderId, admin);
    }
}
