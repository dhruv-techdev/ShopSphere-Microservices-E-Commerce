package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.DeliveryStatus;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpNotificationChannelTest {

    @Mock JavaMailSender mailSender;

    private SmtpNotificationChannel channel;

    @BeforeEach
    void setUp() {
        NotificationProperties props = new NotificationProperties();
        props.setFromAddress("no-reply@shopsphere.test");
        props.setFromName("ShopSphere");
        channel = new SmtpNotificationChannel(mailSender, props);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    private static OutboundMessage message() {
        return new OutboundMessage("jane@example.com", "Order #42 has shipped", "plain", "<p>html</p>");
    }

    @Test
    void send_success_returnsSent_withAddressesAndSubject() throws Exception {
        DeliveryResult result = channel.send(message());

        assertThat(result.status()).isEqualTo(DeliveryStatus.SENT);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("Order #42 has shipped");
        assertThat(sent.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("jane@example.com");
        assertThat(sent.getFrom()[0].toString()).contains("no-reply@shopsphere.test");
    }

    @Test
    void send_smtpError_returnsFailed() {
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(MimeMessage.class));

        DeliveryResult result = channel.send(message());

        assertThat(result.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(result.failureReason()).contains("Connection refused");
    }
}
