package com.shopsphere.notificationservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** US40 — enables the notification retry poller. Disable with NOTIFICATION_RETRY_ENABLED=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.notification.retry.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
