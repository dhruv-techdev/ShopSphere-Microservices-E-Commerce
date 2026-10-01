package com.shopsphere.notificationservice.controller;

import com.shopsphere.notificationservice.dto.AdminNotificationResponse;
import com.shopsphere.notificationservice.dto.NotificationSummaryResponse;
import com.shopsphere.notificationservice.entity.DeliveryStatus;
import com.shopsphere.notificationservice.entity.NotificationType;
import com.shopsphere.notificationservice.service.AdminNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** US46 — notification log (ADMIN only, here and at the gateway). */
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Notifications", description = "Delivery log and manual retries (ADMIN)")
public class AdminNotificationController {

    private final AdminNotificationService service;

    @GetMapping
    @Operation(summary = "Search the notification log", description = "Newest first. `recipient` is a partial match.")
    public Page<AdminNotificationResponse> list(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String recipient,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(status, type, userId, orderId, recipient, pageable);
    }

    @GetMapping("/summary")
    @Operation(summary = "Counts per delivery status")
    public NotificationSummaryResponse summary() {
        return service.summary();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One notification (bodies with one-time links are redacted)")
    public AdminNotificationResponse get(@PathVariable @Positive Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Requeue a FAILED notification for another delivery attempt")
    public AdminNotificationResponse retry(@PathVariable @Positive Long id) {
        return service.retry(id);
    }
}
