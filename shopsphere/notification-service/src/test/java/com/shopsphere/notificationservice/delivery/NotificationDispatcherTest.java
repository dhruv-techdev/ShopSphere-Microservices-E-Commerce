package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.Notification;
import com.shopsphere.notificationservice.entity.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Dispatcher only records once per source event and hands the row to the executor. */
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock NotificationRecorder recorder;
    @Mock DeliveryAttemptExecutor executor;

    private final TemplateModelCodec modelCodec = new TemplateModelCodec();
    private NotificationDispatcher dispatcher;

    private final NotificationDraft draft = new NotificationDraft(
            NotificationType.SHIPMENT_DISPATCHED, 7L, 42L,
            "Order #42 has shipped", "plain text", "evt-1",
            Map.of("trackingNumber", "SSX1"));

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(recorder, executor, modelCodec);
    }

    @Test
    void newEvent_isRecordedWithSerialisedModel_andAttempted() {
        Notification recorded = Notification.builder().id(99L).build();
        when(recorder.recordIfNew(eq(draft), anyString())).thenReturn(Optional.of(recorded));

        dispatcher.deliver(draft);

        ArgumentCaptor<String> modelJson = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordIfNew(eq(draft), modelJson.capture());
        assertThat(modelCodec.read(modelJson.getValue())).containsEntry("trackingNumber", "SSX1");
        verify(executor).attempt(recorded);
    }

    @Test
    void duplicateEvent_isNotAttempted() {
        when(recorder.recordIfNew(eq(draft), anyString())).thenReturn(Optional.empty());

        dispatcher.deliver(draft);

        verify(executor, never()).attempt(any());
    }
}
