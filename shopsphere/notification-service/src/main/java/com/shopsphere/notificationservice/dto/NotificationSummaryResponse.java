package com.shopsphere.notificationservice.dto;

import com.shopsphere.notificationservice.entity.DeliveryStatus;

import java.util.Map;

/** US46 — counts per delivery status (every status present, zero if none). */
public record NotificationSummaryResponse(long total, Map<DeliveryStatus, Long> byStatus) {
}
