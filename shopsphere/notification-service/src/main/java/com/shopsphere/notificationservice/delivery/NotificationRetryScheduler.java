package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.DeliveryStatus;
import com.shopsphere.notificationservice.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * US40 — polls for RETRYING notifications whose next_attempt_at has passed. Each row is claimed
 * with a compare-and-set lease first, so several instances never send the same email twice.
 * A crashed attempt becomes visible again when its lease expires.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRetryScheduler {

    private final NotificationRecorder recorder;
    private final DeliveryAttemptExecutor executor;
    private final NotificationProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.notification.retry.poll-interval-ms:15000}",
            initialDelayString = "${app.notification.retry.initial-poll-delay-ms:20000}")
    public void poll() {
        try {
            retryDue();
        } catch (Exception ex) {
            log.warn("Notification retry poll failed: {}", ex.getMessage(), ex);
        }
    }

    /** @return number of retries attempted in this pass. */
    public int retryDue() {
        Instant now = clock.instant();
        NotificationProperties.Retry retry = properties.getRetry();

        List<Notification> due = recorder.findDueRetries(now, retry.getBatchSize());
        int attempted = 0;
        for (Notification n : due) {
            if (!recorder.claim(n.getId(), n.getNextAttemptAt(), now.plus(retry.getLease()))) {
                continue; // another instance got it
            }
            DeliveryStatus outcome = executor.attempt(n);
            attempted++;
            log.info("Retried notification {} (attempt {}) → {}", n.getId(), n.getAttempts() + 1, outcome);
        }
        if (attempted > 0) {
            log.info("Notification retry pass: {} due, {} attempted", due.size(), attempted);
        }
        return attempted;
    }
}
