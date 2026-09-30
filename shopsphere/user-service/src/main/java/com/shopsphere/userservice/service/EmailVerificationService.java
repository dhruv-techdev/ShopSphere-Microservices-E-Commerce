package com.shopsphere.userservice.service;

import com.shopsphere.common.events.Topics;
import com.shopsphere.common.events.UserEmailVerificationRequestedEvent;
import com.shopsphere.userservice.auth.AuthTokenStore;
import com.shopsphere.userservice.auth.SecureTokens;
import com.shopsphere.userservice.auth.TokenPurpose;
import com.shopsphere.userservice.config.AuthProperties;
import com.shopsphere.userservice.dto.AuthResponse;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.InvalidTokenException;
import com.shopsphere.userservice.messaging.AccountEmailEventRelay;
import com.shopsphere.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** US41 — email verification on registration. */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final AuthTokenStore tokenStore;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher events;
    private final AuthProperties properties;
    private final Clock clock;

    /** Issues a fresh link (invalidating any previous one) and asks notification-service to email it. */
    public void sendVerification(User user) {
        String token = SecureTokens.generate();
        Duration ttl = properties.getVerificationTokenTtl();
        tokenStore.storeOneTime(TokenPurpose.EMAIL_VERIFICATION, SecureTokens.sha256(token), user.getId(), ttl);

        Instant now = clock.instant();
        events.publishEvent(new AccountEmailEventRelay.Outgoing(
                Topics.USER_EMAIL_VERIFICATION_REQUESTED,
                user.getId(),
                UserEmailVerificationRequestedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(UserEmailVerificationRequestedEvent.TYPE)
                        .occurredAt(now)
                        .userId(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .verificationUrl(properties.getFrontendBaseUrl() + "/verify-email?token=" + token)
                        .expiresAt(now.plus(ttl))
                        .build()));
        log.info("Email verification requested for userId={}", user.getId());
    }

    /** Confirms the address and signs the user in (the link proves mailbox ownership). */
    @Transactional
    public AuthResponse confirm(String token) {
        Long userId = tokenStore.consumeOneTime(TokenPurpose.EMAIL_VERIFICATION, SecureTokens.sha256(token))
                .orElseThrow(InvalidTokenException::new);

        User user = userRepository.findById(userId)
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .orElseThrow(InvalidTokenException::new);

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(Boolean.TRUE);
            user.setEmailVerifiedAt(clock.instant());
            userRepository.save(user);
            log.info("Email verified for userId={}", userId);
        }
        return refreshTokenService.issue(user);
    }

    /** Always silent: never reveals whether an account exists or is already verified. */
    public void resend(String email) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .filter(u -> !Boolean.TRUE.equals(u.getEmailVerified()))
                .filter(u -> tokenStore.tryStartCooldown(TokenPurpose.EMAIL_VERIFICATION, u.getId(),
                        properties.getRequestCooldown()))
                .ifPresent(this::sendVerification);
    }
}
