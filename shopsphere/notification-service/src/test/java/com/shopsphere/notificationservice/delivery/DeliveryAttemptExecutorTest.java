package com.shopsphere.notificationservice.delivery;

import com.shopsphere.common.events.NotificationDeadLetterEvent;
import com.shopsphere.notificationservice.entity.DeliveryStatus;
import com.shopsphere.notificationservice.entity.Notification;
import com.shopsphere.notificationservice.entity.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US39/US40 — one delivery attempt and how its outcome is settled (final, retry, or DLQ). */
@ExtendWith(MockitoExtension.class)
class DeliveryAttemptExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final Instant RETRY_AT = NOW.plus(Duration.ofSeconds(30));

    @Mock NotificationRecorder recorder;
    @Mock RecipientResolver recipientResolver;
    @Mock EmailRenderer renderer;
    @Mock NotificationChannel channel;
    @Mock RetryPolicy retryPolicy;
    @Mock DeadLetterPublisher deadLetterPublisher;
    @Mock NotificationMetrics metrics;

    private final TemplateModelCodec modelCodec = new TemplateModelCodec();
    private DeliveryAttemptExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DeliveryAttemptExecutor(recorder, recipientResolver, renderer, channel,
                retryPolicy, deadLetterPublisher, metrics, modelCodec, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(channel.name()).thenReturn("smtp");
    }

    private Notification notification(int attemptsSoFar) {
        return Notification.builder()
                .id(99L).type(NotificationType.SHIPMENT_DISPATCHED)
                .userId(7L).orderId(42L).sourceEventId("evt-1")
                .subject("Order #42 has shipped").body("plain text")
                .templateModel(modelCodec.write(Map.of("trackingNumber", "SSX1")))
                .attempts(attemptsSoFar)
                .build();
    }

    private void recipientFound() {
        when(recipientResolver.resolve(7L)).thenReturn(Optional.of(new RecipientResolver.Recipient("jane@example.com", "Jane")));
        when(renderer.render(eq(NotificationType.SHIPMENT_DISPATCHED), anyMap())).thenReturn("<html>SSX1</html>");
    }

    @Test
    void success_sendsRenderedEmail_andRecordsFinalSent() {
        recipientFound();
        when(channel.send(any())).thenReturn(DeliveryResult.sent("msg-123"));

        DeliveryStatus status = executor.attempt(notification(0));

        assertThat(status).isEqualTo(DeliveryStatus.SENT);

        ArgumentCaptor<OutboundMessage> message = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(channel).send(message.capture());
        assertThat(message.getValue().to()).isEqualTo("jane@example.com");
        assertThat(message.getValue().subject()).isEqualTo("Order #42 has shipped");
        assertThat(message.getValue().textBody()).isEqualTo("plain text");
        assertThat(message.getValue().htmlBody()).isEqualTo("<html>SSX1</html>");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
        verify(renderer).render(eq(NotificationType.SHIPMENT_DISPATCHED), model.capture());
        assertThat(model.getValue())
                .containsEntry("recipientName", "Jane")
                .containsEntry("orderId", 42L)
                .containsEntry("trackingNumber", "SSX1");

        ArgumentCaptor<DeliveryResult> result = ArgumentCaptor.forClass(DeliveryResult.class);
        verify(recorder).recordFinal(eq(99L), eq("smtp"), eq("jane@example.com"), eq(1), result.capture(), eq(NOW));
        assertThat(result.getValue().providerMessageId()).isEqualTo("msg-123");
    }

    @Test
    void noRecipient_isRecordedAsSkipped_withoutSending() {
        when(recipientResolver.resolve(7L)).thenReturn(Optional.empty());

        DeliveryStatus status = executor.attempt(notification(0));

        assertThat(status).isEqualTo(DeliveryStatus.SKIPPED);
        verify(channel, never()).send(any());
        verify(recorder).recordFinal(eq(99L), eq("smtp"), isNull(), eq(1), any(), eq(NOW));
    }

    @Test
    void transientFailure_withBudgetLeft_schedulesRetry() {
        recipientFound();
        when(channel.send(any())).thenReturn(DeliveryResult.transientFailure("SMTP: connection refused"));
        when(retryPolicy.nextAttemptAt(1, NOW)).thenReturn(Optional.of(RETRY_AT));

        DeliveryStatus status = executor.attempt(notification(0));

        assertThat(status).isEqualTo(DeliveryStatus.RETRYING);
        verify(recorder).recordRetryScheduled(eq(99L), eq("smtp"), eq("jane@example.com"), eq(1),
                contains("connection refused"), eq(RETRY_AT), eq(NOW));
        verify(deadLetterPublisher, never()).publish(any());
    }

    @Test
    void recipientLookupError_isTreatedAsTransient_notThrown() {
        when(recipientResolver.resolve(7L)).thenThrow(new ResourceAccessException("user-service timeout"));
        when(retryPolicy.nextAttemptAt(1, NOW)).thenReturn(Optional.of(RETRY_AT));

        DeliveryStatus status = executor.attempt(notification(0));

        assertThat(status).isEqualTo(DeliveryStatus.RETRYING);
        verify(recorder).recordRetryScheduled(eq(99L), eq("smtp"), isNull(), eq(1),
                contains("user-service timeout"), eq(RETRY_AT), eq(NOW));
    }

    @Test
    void permanentFailure_goesStraightToDlq() {
        recipientFound();
        when(channel.send(any())).thenReturn(DeliveryResult.permanentFailure("550 mailbox unavailable"));

        DeliveryStatus status = executor.attempt(notification(0));

        assertThat(status).isEqualTo(DeliveryStatus.DEAD_LETTERED);
        verify(retryPolicy, never()).nextAttemptAt(anyInt(), any());
        verify(recorder).recordDeadLettered(eq(99L), eq("smtp"), eq("jane@example.com"), eq(1),
                contains("550"), eq(NOW));

        ArgumentCaptor<NotificationDeadLetterEvent> event = ArgumentCaptor.forClass(NotificationDeadLetterEvent.class);
        verify(deadLetterPublisher).publish(event.capture());
        assertThat(event.getValue().getNotificationId()).isEqualTo(99L);
        assertThat(event.getValue().getReason()).isEqualTo(DeadLetterReason.PERMANENT_FAILURE.name());
        assertThat(event.getValue().getAttempts()).isEqualTo(1);
        assertThat(event.getValue().getSourceEventId()).isEqualTo("evt-1");
    }

    @Test
    void transientFailure_onLastAttempt_isDeadLetteredAsRetriesExhausted() {
        recipientFound();
        when(channel.send(any())).thenReturn(DeliveryResult.transientFailure("SMTP: timeout"));
        when(retryPolicy.nextAttemptAt(5, NOW)).thenReturn(Optional.empty());

        DeliveryStatus status = executor.attempt(notification(4));

        assertThat(status).isEqualTo(DeliveryStatus.DEAD_LETTERED);
        ArgumentCaptor<NotificationDeadLetterEvent> event = ArgumentCaptor.forClass(NotificationDeadLetterEvent.class);
        verify(deadLetterPublisher).publish(event.capture());
        assertThat(event.getValue().getReason()).isEqualTo(DeadLetterReason.RETRIES_EXHAUSTED.name());
        assertThat(event.getValue().getAttempts()).isEqualTo(5);
    }

    @Test
    void dlqPublishFailure_keepsNotificationInRetryLoop() {
        recipientFound();
        when(channel.send(any())).thenReturn(DeliveryResult.permanentFailure("550 mailbox unavailable"));
        when(retryPolicy.maxDelay()).thenReturn(Duration.ofMinutes(30));
        doThrow(new IllegalStateException("kafka down")).when(deadLetterPublisher).publish(any());

        DeliveryStatus status = executor.attempt(notification(0));

        assertThat(status).isEqualTo(DeliveryStatus.RETRYING);
        verify(recorder).recordRetryScheduled(eq(99L), eq("smtp"), eq("jane@example.com"), eq(1),
                contains("DLQ publish failed"), eq(NOW.plus(Duration.ofMinutes(30))), eq(NOW));
        verify(metrics, never()).deadLettered(any(), any());
    }

    @Test
    void corruptStoredModel_isPermanent() {
        when(recipientResolver.resolve(7L)).thenReturn(Optional.of(new RecipientResolver.Recipient("jane@example.com", "Jane")));
        Notification n = notification(0);
        n.setTemplateModel("{not json");

        DeliveryStatus status = executor.attempt(n);

        assertThat(status).isEqualTo(DeliveryStatus.DEAD_LETTERED);
        verify(channel, never()).send(any());
        verify(recorder).recordDeadLettered(eq(99L), eq("smtp"), eq("jane@example.com"), eq(1),
                contains("Rendering"), eq(NOW));
        verify(deadLetterPublisher).publish(any());
        verify(recorder, never()).recordRetryScheduled(any(), anyString(), any(), anyInt(), anyString(), any(), any());
    }
}
