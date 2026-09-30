package com.shopsphere.notificationservice.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SendGrid v3 Mail Send over plain HTTP (no SDK). 202 Accepted → SENT with X-Message-Id.
 * US40: 408 / 429 / 5xx / I/O errors are transient; other 4xx are permanent.
 * Fails fast at startup if selected without an API key.
 */
@Component
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "sendgrid")
@Slf4j
public class SendGridNotificationChannel implements NotificationChannel {

    private final RestClient restClient;
    private final NotificationProperties properties;

    public SendGridNotificationChannel(RestClient.Builder builder, NotificationProperties properties) {
        String apiKey = properties.getSendgrid().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("SENDGRID_API_KEY must be set when app.notification.channel=sendgrid");
        }
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getSendgrid().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public String name() {
        return "sendgrid";
    }

    @Override
    public DeliveryResult send(OutboundMessage message) {
        // SendGrid requires text/plain before text/html
        List<Map<String, String>> content = new ArrayList<>();
        content.add(Map.of("type", "text/plain", "value", message.textBody()));
        if (message.htmlBody() != null) {
            content.add(Map.of("type", "text/html", "value", message.htmlBody()));
        }

        Map<String, Object> payload = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", message.to())))),
                "from", Map.of("email", properties.getFromAddress(), "name", properties.getFromName()),
                "subject", message.subject(),
                "content", content);

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/v3/mail/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return DeliveryResult.sent(response.getHeaders().getFirst("X-Message-Id"));
        } catch (RestClientResponseException ex) {
            int code = ex.getStatusCode().value();
            String reason = "SendGrid " + code + ": " + ex.getResponseBodyAsString();
            boolean retryable = code == 408 || code == 429 || code >= 500;
            log.warn("SendGrid rejected mail to {} ({}): {}", message.to(), retryable ? "will retry" : "permanent", reason);
            return retryable ? DeliveryResult.transientFailure(reason) : DeliveryResult.permanentFailure(reason);
        } catch (RestClientException ex) {
            log.warn("SendGrid call for {} failed (will retry): {}", message.to(), ex.getMessage());
            return DeliveryResult.transientFailure("SendGrid: " + ex.getMessage());
        }
    }
}
