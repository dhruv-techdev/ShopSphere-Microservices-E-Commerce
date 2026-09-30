package com.shopsphere.notificationservice.delivery;

public enum DeadLetterReason {
    /** The channel said retrying can't help (rejected address, bad credentials, 4xx, template error). */
    PERMANENT_FAILURE,
    /** Transient failures until max-attempts was reached. */
    RETRIES_EXHAUSTED
}
