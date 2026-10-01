package com.shopsphere.notificationservice.controller;

import com.shopsphere.notificationservice.dto.NotificationResponse;
import com.shopsphere.notificationservice.entity.NotificationType;
import com.shopsphere.notificationservice.service.NotificationService;
import com.shopsphere.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** US46 — customers can only read their own notifications (they may contain one-time links). */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "A user's own notification history")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List notifications for a user (owner or ADMIN)",
            description = "Newest first. Optional `type` filter. Admins: see /api/v1/admin/notifications.")
    public Page<NotificationResponse> getByUser(
            @Parameter(description = "User id", required = true, example = "1")
            @PathVariable @Positive Long userId,
            @Parameter(description = "Optional type filter")
            @RequestParam(required = false) NotificationType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!user.isAdmin() && !userId.equals(user.userId())) {
            throw new AccessDeniedException("Notifications belong to another user");
        }
        return notificationService.getByUser(userId, type, pageable);
    }
}
