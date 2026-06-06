package com.shopsphere.notificationservice.controller;

import com.shopsphere.notificationservice.dto.NotificationResponse;
import com.shopsphere.notificationservice.entity.NotificationType;
import com.shopsphere.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "View simulated notification logs")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/user/{userId}")
    @Operation(
            summary = "List notifications for a user",
            description = """
                    Returns notifications addressed to the given user, newest first.

                    - Optional `type` filter (ORDER_PLACED, PAYMENT_SUCCESSFUL, PAYMENT_FAILED).
                    - Pagination via `page`, `size`. Sort is fixed to createdAt desc.
                    - Admin-level notifications (e.g. LOW_STOCK_ALERT) have no userId and
                      do not appear here. A future admin endpoint would surface those.
                    """
    )
    public Page<NotificationResponse> getByUser(
            @Parameter(description = "User id", required = true, example = "1")
            @PathVariable @Positive Long userId,
            @Parameter(description = "Optional type filter")
            @RequestParam(required = false) NotificationType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificationService.getByUser(userId, type, pageable);
    }
}
