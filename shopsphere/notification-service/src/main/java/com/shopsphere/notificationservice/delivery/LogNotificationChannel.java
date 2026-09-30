package com.shopsphere.notificationservice.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default for local runs and tests: nothing leaves the process. */
@Component
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "log", matchIfMissing = true)
@Slf4j
public class LogNotificationChannel implements NotificationChannel {

    @Override
    public String name() {
        return "log";
    }

    @Override
    public DeliveryResult send(OutboundMessage message) {
        log.info("📨 [SIMULATED SEND] to={} subject='{}'", message.to(), message.subject());
        log.debug("📨 [SIMULATED SEND] body: {}", message.textBody());
        return DeliveryResult.simulated();
    }
}
