package com.shopsphere.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/** US41 — user-service asks notification-service to email a verification link. Keyed by userId. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserEmailVerificationRequestedEvent extends BaseEvent {

    public static final String TYPE = "user.email-verification-requested.v1";

    private Long userId;
    private String email;
    private String firstName;
    /** Contains a single-use secret — never log it. */
    private String verificationUrl;
    private Instant expiresAt;
}
