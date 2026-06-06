package com.shopsphere.notificationservice.dto;

import com.shopsphere.notificationservice.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private Long userId;
    private Long orderId;
    private String subject;
    private String body;
    private String deliveryStatus;
    private Instant createdAt;
}
