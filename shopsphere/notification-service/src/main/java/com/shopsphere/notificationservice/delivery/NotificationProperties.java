package com.shopsphere.notificationservice.delivery;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    /** log | smtp | sendgrid */
    private String channel = "log";
    private String fromAddress = "no-reply@shopsphere.local";
    private String fromName = "ShopSphere";
    /** Recipient for system alerts with no user (LOW_STOCK_ALERT). */
    private String adminEmail = "ops@shopsphere.local";
    private Sendgrid sendgrid = new Sendgrid();
    private Retry retry = new Retry();

    @Data
    public static class Sendgrid {
        private String apiKey = "";
        private String baseUrl = "https://api.sendgrid.com";
    }

    /** US40 */
    @Data
    public static class Retry {
        private boolean enabled = true;
        /** Total send attempts including the first one. */
        private int maxAttempts = 5;
        private Duration initialDelay = Duration.ofSeconds(30);
        private double multiplier = 2.0;
        private Duration maxDelay = Duration.ofMinutes(30);
        /** How long a claimed retry is hidden from other pollers (crash safety). */
        private Duration lease = Duration.ofMinutes(5);
        private int batchSize = 50;
        private long pollIntervalMs = 15_000;
        private long initialPollDelayMs = 20_000;
    }
}
