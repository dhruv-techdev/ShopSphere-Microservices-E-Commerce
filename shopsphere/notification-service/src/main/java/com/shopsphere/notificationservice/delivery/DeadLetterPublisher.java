package com.shopsphere.notificationservice.delivery;

import com.shopsphere.common.events.NotificationDeadLetterEvent;
import com.shopsphere.common.events.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** US40 — synchronous (acked) publish to notification.dlq; throws if the broker didn't confirm. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterPublisher {

    private static final long ACK_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(NotificationDeadLetterEvent event) {
        try {
            kafkaTemplate.send(Topics.NOTIFICATION_DLQ, String.valueOf(event.getNotificationId()), event)
                    .get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Routed notification {} to {} ({})", event.getNotificationId(), Topics.NOTIFICATION_DLQ, event.getReason());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing to DLQ", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("DLQ publish not acknowledged: " + ex.getMessage(), ex);
        }
    }
}
