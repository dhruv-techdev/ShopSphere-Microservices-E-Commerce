package com.shopsphere.notificationservice.delivery;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/**
 * SMTP via spring.mail.* — Mailpit in Docker, any real relay elsewhere. Sends text + HTML.
 * US40: failures are classified — rejected recipients / bad credentials / malformed messages are
 * permanent; connection and server problems are transient.
 */
@Component
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "smtp")
@RequiredArgsConstructor
@Slf4j
public class SmtpNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    @Override
    public String name() {
        return "smtp";
    }

    @Override
    public DeliveryResult send(OutboundMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(properties.getFromAddress(), properties.getFromName());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            if (message.htmlBody() != null) {
                helper.setText(message.textBody(), message.htmlBody());
            } else {
                helper.setText(message.textBody(), false);
            }

            mailSender.send(mime);
            return DeliveryResult.sent(mime.getMessageID());
        } catch (MailAuthenticationException ex) {
            log.warn("SMTP authentication failed: {}", ex.getMessage());
            return DeliveryResult.permanentFailure("SMTP authentication failed: " + ex.getMessage());
        } catch (MailSendException ex) {
            if (recipientRejected(ex)) {
                log.warn("SMTP rejected recipient {}: {}", message.to(), ex.getMessage());
                return DeliveryResult.permanentFailure("SMTP rejected recipient: " + ex.getMessage());
            }
            log.warn("SMTP send to {} failed (will retry): {}", message.to(), ex.getMessage());
            return DeliveryResult.transientFailure("SMTP: " + ex.getMessage());
        } catch (MailParseException | MailPreparationException ex) {
            return DeliveryResult.permanentFailure("SMTP message invalid: " + ex.getMessage());
        } catch (MailException ex) {
            log.warn("SMTP send to {} failed (will retry): {}", message.to(), ex.getMessage());
            return DeliveryResult.transientFailure("SMTP: " + ex.getMessage());
        } catch (MessagingException | UnsupportedEncodingException ex) {
            // Raised while building the message, e.g. a malformed address — retrying won't fix it.
            return DeliveryResult.permanentFailure("Invalid message: " + ex.getMessage());
        }
    }

    private static boolean recipientRejected(MailSendException ex) {
        return ex.getFailedMessages().values().stream()
                .anyMatch(e -> e instanceof SendFailedException sfe
                        && sfe.getInvalidAddresses() != null
                        && sfe.getInvalidAddresses().length > 0);
    }
}
