package com.shopsphere.userservice.service;

import com.shopsphere.common.events.Topics;
import com.shopsphere.common.events.UserPasswordResetRequestedEvent;
import com.shopsphere.userservice.auth.AuthTokenStore;
import com.shopsphere.userservice.auth.SecureTokens;
import com.shopsphere.userservice.auth.TokenPurpose;
import com.shopsphere.userservice.config.AuthProperties;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.InvalidTokenException;
import com.shopsphere.userservice.messaging.AccountEmailEventRelay;
import com.shopsphere.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthTokenStore tokenStore;
    @Mock RefreshTokenService refreshTokenService;
    @Mock ApplicationEventPublisher events;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties();
        props.setFrontendBaseUrl("https://shop.test");
        props.setPasswordResetTokenTtl(Duration.ofMinutes(30));
        props.setRequestCooldown(Duration.ofSeconds(60));
        service = new PasswordResetService(userRepository, passwordEncoder, tokenStore, refreshTokenService,
                events, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User user(boolean verified) {
        return User.builder().id(7L).email("jane@example.com").firstName("Jane").passwordHash("old")
                .role(Role.CUSTOMER).enabled(true).emailVerified(verified).build();
    }

    @Test
    void request_knownUser_storesHashedToken_andPublishesResetLink() {
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user(true)));
        when(tokenStore.tryStartCooldown(TokenPurpose.PASSWORD_RESET, 7L, Duration.ofSeconds(60))).thenReturn(true);

        service.requestReset("jane@example.com");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(tokenStore).storeOneTime(eq(TokenPurpose.PASSWORD_RESET), hash.capture(), eq(7L), eq(Duration.ofMinutes(30)));

        ArgumentCaptor<AccountEmailEventRelay.Outgoing> out = ArgumentCaptor.forClass(AccountEmailEventRelay.Outgoing.class);
        verify(events).publishEvent(out.capture());
        assertThat(out.getValue().topic()).isEqualTo(Topics.USER_PASSWORD_RESET_REQUESTED);
        UserPasswordResetRequestedEvent event = (UserPasswordResetRequestedEvent) out.getValue().payload();
        assertThat(event.getResetUrl()).startsWith("https://shop.test/reset-password?token=");
        assertThat(event.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));

        String rawToken = event.getResetUrl().substring(event.getResetUrl().indexOf("token=") + 6);
        assertThat(SecureTokens.sha256(rawToken)).isEqualTo(hash.getValue());
    }

    @Test
    void request_unknownEmail_doesNothingVisible() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        service.requestReset("ghost@example.com");

        verifyNoInteractions(tokenStore, events);
    }

    @Test
    void request_withinCooldown_sendsNothing() {
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user(true)));
        when(tokenStore.tryStartCooldown(TokenPurpose.PASSWORD_RESET, 7L, Duration.ofSeconds(60))).thenReturn(false);

        service.requestReset("jane@example.com");

        verify(tokenStore, never()).storeOneTime(any(), any(), any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void confirm_validToken_setsPassword_verifiesEmail_andRevokesAllSessions() {
        User user = user(false);
        when(tokenStore.consumeOneTime(TokenPurpose.PASSWORD_RESET, SecureTokens.sha256("tok"))).thenReturn(Optional.of(7L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("n3w-password")).thenReturn("new-hash");

        service.confirm("tok", "n3w-password");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getEmailVerified()).isTrue();
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAll(7L);
    }

    @Test
    void confirm_invalidOrUsedToken_isRejected() {
        when(tokenStore.consumeOneTime(TokenPurpose.PASSWORD_RESET, SecureTokens.sha256("used"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm("used", "n3w-password")).isInstanceOf(InvalidTokenException.class);

        verifyNoInteractions(userRepository, passwordEncoder, refreshTokenService);
    }
}
