package com.shopsphere.userservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** US41 */
@Data
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** When true, login is refused (403) until the email address is confirmed. */
    private boolean requireVerifiedEmail = true;

    /** Base of the links put in emails, e.g. {base}/verify-email?token=... */
    private String frontendBaseUrl = "http://localhost:3000";

    private Duration refreshTokenTtl = Duration.ofDays(14);
    private Duration verificationTokenTtl = Duration.ofHours(24);
    private Duration passwordResetTokenTtl = Duration.ofMinutes(30);

    /** Minimum gap between resend-verification / reset requests per account. */
    private Duration requestCooldown = Duration.ofSeconds(60);
}
