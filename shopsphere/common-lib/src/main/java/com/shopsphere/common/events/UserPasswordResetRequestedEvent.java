package com.shopsphere.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/** US41 — user-service asks notification-service to email a password-reset link. Keyed by userId. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserPasswordResetRequestedEvent extends BaseEvent {

    public static final String TYPE = "user.password-reset-requested.v1";

    private Long userId;
    private String email;
    private String firstName;
    /** Contains a single-use secret — never log it. */
    private String resetUrl;
    private Instant expiresAt;
}
