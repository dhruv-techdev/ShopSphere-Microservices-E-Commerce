package com.shopsphere.notificationservice.delivery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * US40 — exponential backoff. After attempt n (1-based) the next try waits
 * initialDelay * multiplier^(n-1), capped at maxDelay. No next try once maxAttempts is reached.
 */
@Component
@RequiredArgsConstructor
public class RetryPolicy {

    private final NotificationProperties properties;

    public Duration backoffAfter(int attemptNumber) {
        NotificationProperties.Retry retry = properties.getRetry();
        double factor = Math.pow(retry.getMultiplier(), Math.max(0, attemptNumber - 1));
        double millis = retry.getInitialDelay().toMillis() * factor;
        long capped = (long) Math.min(millis, retry.getMaxDelay().toMillis());
        return Duration.ofMillis(capped);
    }

    public Optional<Instant> nextAttemptAt(int attemptsMade, Instant now) {
        if (attemptsMade >= properties.getRetry().getMaxAttempts()) {
            return Optional.empty();
        }
        return Optional.of(now.plus(backoffAfter(attemptsMade)));
    }

    public Duration maxDelay() {
        return properties.getRetry().getMaxDelay();
    }
}
