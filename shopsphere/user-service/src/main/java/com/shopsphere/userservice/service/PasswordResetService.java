package com.shopsphere.userservice.service;

import com.shopsphere.common.events.Topics;
import com.shopsphere.common.events.UserPasswordResetRequestedEvent;
import com.shopsphere.userservice.auth.AuthTokenStore;
import com.shopsphere.userservice.auth.SecureTokens;
import com.shopsphere.userservice.auth.TokenPurpose;
import com.shopsphere.userservice.config.AuthProperties;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.InvalidTokenException;
import com.shopsphere.userservice.messaging.AccountEmailEventRelay;
import com.shopsphere.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** US41 — "forgot password": request a link by email, then set a new password with it. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenStore tokenStore;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher events;
    private final AuthProperties properties;
    private final Clock clock;

    /** Always silent: the caller learns nothing about whether the email is registered. */
    public void requestReset(String email) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .filter(u -> tokenStore.tryStartCooldown(TokenPurpose.PASSWORD_RESET, u.getId(),
                        properties.getRequestCooldown()))
                .ifPresent(this::sendResetLink);
    }

    @Transactional
    public void confirm(String token, String newPassword) {
        Long userId = tokenStore.consumeOneTime(TokenPurpose.PASSWORD_RESET, SecureTokens.sha256(token))
                .orElseThrow(InvalidTokenException::new);

        User user = userRepository.findById(userId)
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .orElseThrow(InvalidTokenException::new);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            // Opening the emailed link proves ownership of the address.
            user.setEmailVerified(Boolean.TRUE);
            user.setEmailVerifiedAt(clock.instant());
        }
        userRepository.save(user);

        // Every existing session may belong to whoever knew the old password.
        refreshTokenService.revokeAll(userId);
        log.info("Password reset completed for userId={} — all sessions revoked", userId);
    }

    private void sendResetLink(User user) {
        String token = SecureTokens.generate();
        Duration ttl = properties.getPasswordResetTokenTtl();
        tokenStore.storeOneTime(TokenPurpose.PASSWORD_RESET, SecureTokens.sha256(token), user.getId(), ttl);

        Instant now = clock.instant();
        events.publishEvent(new AccountEmailEventRelay.Outgoing(
                Topics.USER_PASSWORD_RESET_REQUESTED,
                user.getId(),
                UserPasswordResetRequestedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(UserPasswordResetRequestedEvent.TYPE)
                        .occurredAt(now)
                        .userId(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .resetUrl(properties.getFrontendBaseUrl() + "/reset-password?token=" + token)
                        .expiresAt(now.plus(ttl))
                        .build()));
        log.info("Password reset requested for userId={}", user.getId());
    }
}
