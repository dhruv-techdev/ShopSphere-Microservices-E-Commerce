package com.shopsphere.notificationservice.entity;

public enum DeliveryStatus {
    /** Recorded, first send not finished yet. */
    PENDING,
    /** Transient failure; next attempt scheduled at next_attempt_at (US40). */
    RETRYING,
    /** Accepted by the SMTP server / SendGrid. */
    SENT,
    /** Handed to notification.dlq; the DLQ consumer will mark it FAILED (US40). */
    DEAD_LETTERED,
    /** Terminal failure (set by the DLQ consumer); see failureReason. */
    FAILED,
    /** "log" channel — nothing actually sent (local/dev). */
    SIMULATED,
    /** No deliverable recipient (unknown/disabled user, no email). */
    SKIPPED
}
