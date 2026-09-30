package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.DeliveryStatus;
import com.shopsphere.notificationservice.entity.Notification;
import com.shopsphere.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Short transactions around the (slow, external) send. Every state change goes through here. */
@Service
@RequiredArgsConstructor
public class NotificationRecorder {

    static final int MAX_REASON = 500;

    private final NotificationRepository repository;

    /** @return the new PENDING notification, or empty if this source event was already handled. */
    @Transactional
    public Optional<Notification> recordIfNew(NotificationDraft draft, String templateModelJson) {
        if (repository.existsBySourceEventId(draft.sourceEventId())) {
            return Optional.empty();
        }
        return Optional.of(repository.save(Notification.builder()
                .type(draft.type())
                .userId(draft.userId())
                .orderId(draft.orderId())
                .subject(draft.subject())
                .body(draft.body())
                .sourceEventId(draft.sourceEventId())
                .templateModel(templateModelJson)
                .deliveryStatus(DeliveryStatus.PENDING)
                .attempts(0)
                .build()));
    }

    /** SENT / SIMULATED / SKIPPED. */
    @Transactional
    public void recordFinal(Long id, String channel, String recipient, int attempts, DeliveryResult result, Instant at) {
        update(id, n -> {
            applyAttempt(n, channel, recipient, attempts, at);
            n.setDeliveryStatus(result.status());
            n.setProviderMessageId(result.providerMessageId());
            n.setFailureReason(truncate(result.failureReason()));
            n.setNextAttemptAt(null);
            if (result.status() == DeliveryStatus.SENT || result.status() == DeliveryStatus.SIMULATED) {
                n.setSentAt(at);
            }
        });
    }

    @Transactional
    public void recordRetryScheduled(Long id, String channel, String recipient, int attempts,
                                     String reason, Instant nextAttemptAt, Instant at) {
        update(id, n -> {
            applyAttempt(n, channel, recipient, attempts, at);
            n.setDeliveryStatus(DeliveryStatus.RETRYING);
            n.setFailureReason(truncate(reason));
            n.setNextAttemptAt(nextAttemptAt);
        });
    }

    /** Written BEFORE publishing to the DLQ so the DLQ consumer can never be overtaken. */
    @Transactional
    public void recordDeadLettered(Long id, String channel, String recipient, int attempts, String reason, Instant at) {
        update(id, n -> {
            applyAttempt(n, channel, recipient, attempts, at);
            n.setDeliveryStatus(DeliveryStatus.DEAD_LETTERED);
            n.setFailureReason(truncate(reason));
            n.setNextAttemptAt(null);
        });
    }

    /** DLQ consumer: terminal FAILED. Idempotent; never overrides a successful delivery. */
    @Transactional
    public boolean markFailedFromDeadLetter(Long id, String deadLetterReason, Integer attempts,
                                            String lastError, Instant deadLetteredAt) {
        return repository.findById(id)
                .filter(n -> EnumSet.of(DeliveryStatus.DEAD_LETTERED, DeliveryStatus.RETRYING, DeliveryStatus.PENDING)
                        .contains(n.getDeliveryStatus()))
                .map(n -> {
                    n.setDeliveryStatus(DeliveryStatus.FAILED);
                    n.setNextAttemptAt(null);
                    n.setDeadLetteredAt(deadLetteredAt);
                    n.setFailureReason(truncate("[" + deadLetterReason + " after " + attempts + " attempt(s)] " + lastError));
                    repository.save(n);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<Notification> findDueRetries(Instant now, int limit) {
        return repository.findDue(DeliveryStatus.RETRYING, now, PageRequest.of(0, limit));
    }

    /** @return true if this caller won the row (see NotificationRepository#claim). */
    @Transactional
    public boolean claim(Long id, Instant expectedNextAttemptAt, Instant leaseUntil) {
        return repository.claim(id, DeliveryStatus.RETRYING, expectedNextAttemptAt, leaseUntil) == 1;
    }

    private static void applyAttempt(Notification n, String channel, String recipient, int attempts, Instant at) {
        n.setChannel(channel);
        if (recipient != null) {
            n.setRecipient(recipient);
        }
        n.setAttempts(attempts);
        n.setLastAttemptAt(at);
    }

    private void update(Long id, Consumer<Notification> change) {
        repository.findById(id).ifPresent(n -> {
            change.accept(n);
            repository.save(n);
        });
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_REASON ? value : value.substring(0, MAX_REASON);
    }
}
