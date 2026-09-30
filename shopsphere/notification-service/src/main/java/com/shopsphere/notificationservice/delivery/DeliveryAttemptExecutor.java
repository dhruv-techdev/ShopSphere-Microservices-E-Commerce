package com.shopsphere.notificationservice.delivery;

import com.shopsphere.common.events.NotificationDeadLetterEvent;
import com.shopsphere.notificationservice.entity.DeliveryStatus;
import com.shopsphere.notificationservice.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.exceptions.TemplateEngineException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * US40 — one delivery attempt (first try or retry) and what happens next:
 *   success/skip           → recordFinal
 *   transient, budget left → RETRYING with exponential backoff
 *   permanent or exhausted → DEAD_LETTERED + notification.dlq (DLQ consumer marks FAILED)
 * Never throws for delivery problems; everything ends up on the row and in metrics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryAttemptExecutor {

    private final NotificationRecorder recorder;
    private final RecipientResolver recipientResolver;
    private final EmailRenderer renderer;
    private final NotificationChannel channel;
    private final RetryPolicy retryPolicy;
    private final DeadLetterPublisher deadLetterPublisher;
    private final NotificationMetrics metrics;
    private final TemplateModelCodec modelCodec;
    private final Clock clock;

    public DeliveryStatus attempt(Notification n) {
        int attemptNumber = (n.getAttempts() == null ? 0 : n.getAttempts()) + 1;
        String recipient = null;
        DeliveryResult result;

        try {
            Optional<RecipientResolver.Recipient> to = recipientResolver.resolve(n.getUserId());
            if (to.isEmpty()) {
                result = DeliveryResult.skipped("No deliverable email address for userId=" + n.getUserId());
            } else {
                recipient = to.get().email();
                Map<String, Object> model = modelCodec.read(n.getTemplateModel());
                model.put("recipientName", to.get().displayName());
                model.put("subject", n.getSubject());
                model.put("body", n.getBody());
                model.put("orderId", n.getOrderId());

                String html = renderer.render(n.getType(), model);
                result = channel.send(new OutboundMessage(recipient, n.getSubject(), n.getBody(), html));
            }
        } catch (TemplateEngineException | IllegalArgumentException ex) {
            // Broken template / corrupt stored model — retrying renders the same thing.
            result = DeliveryResult.permanentFailure("Rendering: " + ex.getMessage());
        } catch (Exception ex) {
            // e.g. user-service unreachable — try again later.
            result = DeliveryResult.transientFailure(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }

        return settle(n, attemptNumber, recipient, result);
    }

    private DeliveryStatus settle(Notification n, int attemptNumber, String recipient, DeliveryResult result) {
        Instant now = clock.instant();
        String channelName = channel.name();
        metrics.attempt(n.getType(), channelName, result.status(), attemptNumber);

        if (result.status() != DeliveryStatus.FAILED) {
            recorder.recordFinal(n.getId(), channelName, recipient, attemptNumber, result, now);
            metrics.terminal(n.getType(), result.status(), attemptNumber);
            log.info("📨 [{}] notification={} type={} to={} status={} attempt={}",
                    channelName, n.getId(), n.getType(), recipient, result.status(), attemptNumber);
            return result.status();
        }

        Optional<Instant> retryAt = result.retryable()
                ? retryPolicy.nextAttemptAt(attemptNumber, now)
                : Optional.empty();

        if (retryAt.isPresent()) {
            recorder.recordRetryScheduled(n.getId(), channelName, recipient, attemptNumber,
                    result.failureReason(), retryAt.get(), now);
            metrics.retryScheduled(n.getType(), channelName);
            log.warn("Notification {} attempt {} failed ({}); retrying at {}",
                    n.getId(), attemptNumber, result.failureReason(), retryAt.get());
            return DeliveryStatus.RETRYING;
        }

        DeadLetterReason reason = result.retryable()
                ? DeadLetterReason.RETRIES_EXHAUSTED
                : DeadLetterReason.PERMANENT_FAILURE;

        recorder.recordDeadLettered(n.getId(), channelName, recipient, attemptNumber, result.failureReason(), now);
        try {
            deadLetterPublisher.publish(NotificationDeadLetterEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(NotificationDeadLetterEvent.TYPE)
                    .occurredAt(now)
                    .notificationId(n.getId())
                    .notificationType(n.getType().name())
                    .userId(n.getUserId())
                    .orderId(n.getOrderId())
                    .sourceEventId(n.getSourceEventId())
                    .channel(channelName)
                    .recipient(recipient)
                    .attempts(attemptNumber)
                    .reason(reason.name())
                    .lastError(result.failureReason())
                    .failedAt(now)
                    .build());
        } catch (RuntimeException ex) {
            // Couldn't park it — keep it in the retry loop so it isn't lost.
            Instant later = now.plus(retryPolicy.maxDelay());
            recorder.recordRetryScheduled(n.getId(), channelName, recipient, attemptNumber,
                    "DLQ publish failed (" + ex.getMessage() + "); last error: " + result.failureReason(), later, now);
            log.error("Notification {} could not be dead-lettered: {} — retrying at {}", n.getId(), ex.getMessage(), later);
            return DeliveryStatus.RETRYING;
        }

        metrics.deadLettered(n.getType(), reason);
        metrics.terminal(n.getType(), DeliveryStatus.DEAD_LETTERED, attemptNumber);
        log.error("Notification {} dead-lettered after {} attempt(s): {} — {}",
                n.getId(), attemptNumber, reason, result.failureReason());
        return DeliveryStatus.DEAD_LETTERED;
    }
}
