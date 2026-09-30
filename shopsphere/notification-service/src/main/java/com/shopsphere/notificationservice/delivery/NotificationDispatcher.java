package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Entry point from event handlers: record once (idempotent per source event), then make the
 * first delivery attempt. Retries and the DLQ are handled by {@link DeliveryAttemptExecutor}
 * and {@link NotificationRetryScheduler}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final NotificationRecorder recorder;
    private final DeliveryAttemptExecutor executor;
    private final TemplateModelCodec modelCodec;

    public void deliver(NotificationDraft draft) {
        Optional<Notification> recorded = recorder.recordIfNew(draft, modelCodec.write(draft.model()));
        if (recorded.isEmpty()) {
            log.info("Skipping {} for sourceEventId={} — already processed", draft.type(), draft.sourceEventId());
            return;
        }
        executor.attempt(recorded.get());
    }
}
