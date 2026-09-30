package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.DeliveryStatus;

/**
 * Outcome of one send attempt. For FAILED, {@code retryable} says whether trying again
 * could help (network, throttling, 5xx) or not (rejected address, bad credentials, 4xx).
 */
public record DeliveryResult(DeliveryStatus status, String providerMessageId, String failureReason, boolean retryable) {

    public static DeliveryResult sent(String providerMessageId) {
        return new DeliveryResult(DeliveryStatus.SENT, providerMessageId, null, false);
    }

    public static DeliveryResult simulated() {
        return new DeliveryResult(DeliveryStatus.SIMULATED, null, null, false);
    }

    public static DeliveryResult skipped(String reason) {
        return new DeliveryResult(DeliveryStatus.SKIPPED, null, reason, false);
    }

    /** Worth retrying later. */
    public static DeliveryResult transientFailure(String reason) {
        return new DeliveryResult(DeliveryStatus.FAILED, null, reason, true);
    }

    /** Retrying won't help — straight to the DLQ. */
    public static DeliveryResult permanentFailure(String reason) {
        return new DeliveryResult(DeliveryStatus.FAILED, null, reason, false);
    }
}
