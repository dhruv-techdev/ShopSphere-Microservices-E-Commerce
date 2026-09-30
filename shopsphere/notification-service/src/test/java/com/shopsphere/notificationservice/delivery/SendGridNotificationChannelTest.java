package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.DeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class SendGridNotificationChannelTest {

    private MockRestServiceServer server;
    private SendGridNotificationChannel channel;

    private static NotificationProperties props(String apiKey) {
        NotificationProperties props = new NotificationProperties();
        props.setFromAddress("no-reply@shopsphere.test");
        props.getSendgrid().setApiKey(apiKey);
        props.getSendgrid().setBaseUrl("https://sendgrid.test");
        return props;
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        channel = new SendGridNotificationChannel(builder, props("SG.test-key"));
    }

    private static OutboundMessage message() {
        return new OutboundMessage("jane@example.com", "Order #42 has shipped", "plain", "<p>html</p>");
    }

    @Test
    void accepted_returnsSentWithMessageId() {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("X-Message-Id", "sg-abc123");

        server.expect(requestTo("https://sendgrid.test/v3/mail/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer SG.test-key"))
                .andExpect(jsonPath("$.personalizations[0].to[0].email").value("jane@example.com"))
                .andExpect(jsonPath("$.subject").value("Order #42 has shipped"))
                .andExpect(jsonPath("$.content[0].type").value("text/plain"))
                .andExpect(jsonPath("$.content[1].type").value("text/html"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).headers(responseHeaders));

        DeliveryResult result = channel.send(message());

        server.verify();
        assertThat(result.status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(result.providerMessageId()).isEqualTo("sg-abc123");
    }

    @Test
    void rejected_returnsFailedWithStatusAndBody() {
        server.expect(requestTo("https://sendgrid.test/v3/mail/send"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"message\":\"invalid key\"}]}"));

        DeliveryResult result = channel.send(message());

        assertThat(result.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(result.failureReason()).contains("401").contains("invalid key");
    }

    @Test
    void missingApiKey_failsFastAtStartup() {
        assertThatThrownBy(() -> new SendGridNotificationChannel(RestClient.builder(), props("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SENDGRID_API_KEY");
    }
}
