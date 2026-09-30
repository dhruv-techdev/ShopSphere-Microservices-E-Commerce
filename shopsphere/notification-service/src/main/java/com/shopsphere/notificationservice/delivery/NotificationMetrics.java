package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.DeliveryStatus;
import com.shopsphere.notificationservice.entity.NotificationType;
import com.shopsphere.notificationservice.repository.NotificationRepository;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * US40 — exposed at /actuator/metrics and /actuator/prometheus:
 *   notification.delivery.retries                 counter  retries scheduled         (type, channel)
 *   notification.delivery.attempts                counter  every send attempt        (type, channel, outcome, retry)
 *   notification.delivery.attempts.per.notification summary attempts at terminal state (type, outcome)
 *   notification.dlq.routed                       counter  sent to notification.dlq  (type, reason)
 *   notification.dlq.consumed                     counter  processed by DLQ consumer (reason, updated)
 *   notification.retry.pending                    gauge    rows currently RETRYING
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry registry;

    public NotificationMetrics(MeterRegistry registry, NotificationRepository repository) {
        this.registry = registry;
        Gauge.builder("notification.retry.pending", repository,
                        r -> r.countByDeliveryStatus(DeliveryStatus.RETRYING))
                .description("Notifications waiting for a retry")
                .register(registry);
    }

    public void attempt(NotificationType type, String channel, DeliveryStatus outcome, int attemptNumber) {
        registry.counter("notification.delivery.attempts",
                "type", type.name(),
                "channel", channel,
                "outcome", outcome.name(),
                "retry", String.valueOf(attemptNumber > 1)).increment();
    }

    public void retryScheduled(NotificationType type, String channel) {
        registry.counter("notification.delivery.retries",
                "type", type.name(),
                "channel", channel).increment();
    }

    public void terminal(NotificationType type, DeliveryStatus outcome, int attempts) {
        DistributionSummary.builder("notification.delivery.attempts.per.notification")
                .description("Send attempts a notification needed before reaching a terminal state")
                .tags("type", type.name(), "outcome", outcome.name())
                .register(registry)
                .record(attempts);
    }

    public void deadLettered(NotificationType type, DeadLetterReason reason) {
        registry.counter("notification.dlq.routed",
                "type", type.name(),
                "reason", reason.name()).increment();
    }

    public void deadLetterConsumed(String reason, boolean updated) {
        registry.counter("notification.dlq.consumed",
                "reason", reason == null ? "UNKNOWN" : reason,
                "updated", String.valueOf(updated)).increment();
    }
}
