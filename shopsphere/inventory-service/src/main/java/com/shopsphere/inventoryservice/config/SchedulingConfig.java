package com.shopsphere.inventoryservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** US37 — enables the reservation expiry sweeper. Disable with RESERVATION_SWEEPER_ENABLED=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.inventory.reservation.sweeper-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
