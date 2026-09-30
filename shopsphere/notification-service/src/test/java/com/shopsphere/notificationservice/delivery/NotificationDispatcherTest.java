package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.DeliveryStatus;
import com.shopsphere.notificationservice.entity.Notification;
import com.shopsphere.notificationservice.entity.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock NotificationRecorder recorder;
    @Mock RecipientResolver recipientResolver;
    @Mock EmailRenderer renderer;
    @Mock NotificationChannel channel;

    @InjectMocks NotificationDispatcher dispatcher;

    private final NotificationDraft draft = new NotificationDraft(
            NotificationType.SHIPMENT_DISPATCHED, 7L, 42L,
            "Order #42 has shipped", "plain text", "evt-1",
            Map.of("trackingNumber", "SSX1"));

    @BeforeEach
    void setUp() {
        lenient().when(channel.name()).thenReturn("smtp");
    }

    private void recordedAsNew() {
        when(recorder.recordIfNew(draft)).thenReturn(Optional.of(Notification.builder().id(99L).build()));
    }

    @Test
    void success_sendsRenderedEmail_andRecordsSent() {
        recordedAsNew();
        when(recipientResolver.resolve(7L)).thenReturn(Optional.of(new RecipientResolver.Recipient("jane@example.com", "Jane")));
        when(renderer.render(eq(NotificationType.SHIPMENT_DISPATCHED), anyMap())).thenReturn("<html>SSX1</html>");
        when(channel.send(any())).thenReturn(DeliveryResult.sent("msg-123"));

        dispatcher.deliver(draft);

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
        verify(recorder).recordResult(eq(99L), eq("smtp"), eq("jane@example.com"), result.capture());
        assertThat(result.getValue().status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(result.getValue().providerMessageId()).isEqualTo("msg-123");
    }

    @Test
    void duplicateEvent_sendsNothing() {
        when(recorder.recordIfNew(draft)).thenReturn(Optional.empty());

        dispatcher.deliver(draft);

        verifyNoInteractions(recipientResolver, renderer);
        verify(channel, never()).send(any());
        verify(recorder, never()).recordResult(any(), any(), any(), any());
    }

    @Test
    void channelFailure_isRecordedAsFailed() {
        recordedAsNew();
        when(recipientResolver.resolve(7L)).thenReturn(Optional.of(new RecipientResolver.Recipient("jane@example.com", "Jane")));
        when(renderer.render(any(), anyMap())).thenReturn("<html/>");
        when(channel.send(any())).thenReturn(DeliveryResult.failed("SMTP: connection refused"));

        dispatcher.deliver(draft);

        ArgumentCaptor<DeliveryResult> result = ArgumentCaptor.forClass(DeliveryResult.class);
        verify(recorder).recordResult(eq(99L), eq("smtp"), eq("jane@example.com"), result.capture());
        assertThat(result.getValue().status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(result.getValue().failureReason()).contains("connection refused");
    }

    @Test
    void noRecipient_isRecordedAsSkipped_withoutSending() {
        recordedAsNew();
        when(recipientResolver.resolve(7L)).thenReturn(Optional.empty());

        dispatcher.deliver(draft);

        verify(channel, never()).send(any());
        ArgumentCaptor<DeliveryResult> result = ArgumentCaptor.forClass(DeliveryResult.class);
        verify(recorder).recordResult(eq(99L), eq("smtp"), isNull(), result.capture());
        assertThat(result.getValue().status()).isEqualTo(DeliveryStatus.SKIPPED);
    }

    @Test
    void recipientLookupError_isRecordedAsFailed_notThrown() {
        recordedAsNew();
        when(recipientResolver.resolve(7L)).thenThrow(new ResourceAccessException("user-service timeout"));

        dispatcher.deliver(draft);

        ArgumentCaptor<DeliveryResult> result = ArgumentCaptor.forClass(DeliveryResult.class);
        verify(recorder).recordResult(eq(99L), eq("smtp"), isNull(), result.capture());
        assertThat(result.getValue().status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(result.getValue().failureReason()).contains("user-service timeout");
    }
}
