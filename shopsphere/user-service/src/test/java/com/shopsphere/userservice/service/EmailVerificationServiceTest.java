package com.shopsphere.userservice.service;

import com.shopsphere.common.events.Topics;
import com.shopsphere.common.events.UserEmailVerificationRequestedEvent;
import com.shopsphere.userservice.auth.AuthTokenStore;
import com.shopsphere.userservice.auth.SecureTokens;
import com.shopsphere.userservice.auth.TokenPurpose;
import com.shopsphere.userservice.config.AuthProperties;
import com.shopsphere.userservice.dto.AuthResponse;
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
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Mock UserRepository userRepository;
    @Mock AuthTokenStore tokenStore;
    @Mock RefreshTokenService refreshTokenService;
    @Mock ApplicationEventPublisher events;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties();
        props.setFrontendBaseUrl("https://shop.test");
        props.setVerificationTokenTtl(Duration.ofHours(24));
        props.setRequestCooldown(Duration.ofSeconds(60));
        service = new EmailVerificationService(userRepository, tokenStore, refreshTokenService, events,
                props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User user(boolean verified) {
        return User.builder().id(7L).email("jane@example.com").firstName("Jane")
                .role(Role.CUSTOMER).enabled(true).emailVerified(verified).build();
    }

    @Test
    void sendVerification_storesHash_andPublishesLinkWithTheRawToken() {
        service.sendVerification(user(false));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(tokenStore).storeOneTime(eq(TokenPurpose.EMAIL_VERIFICATION), hash.capture(), eq(7L), eq(Duration.ofHours(24)));

        ArgumentCaptor<AccountEmailEventRelay.Outgoing> out = ArgumentCaptor.forClass(AccountEmailEventRelay.Outgoing.class);
        verify(events).publishEvent(out.capture());
        assertThat(out.getValue().topic()).isEqualTo(Topics.USER_EMAIL_VERIFICATION_REQUESTED);

        UserEmailVerificationRequestedEvent event = (UserEmailVerificationRequestedEvent) out.getValue().payload();
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(event.getVerificationUrl()).startsWith("https://shop.test/verify-email?token=");

        String rawToken = event.getVerificationUrl().substring(event.getVerificationUrl().indexOf("token=") + 6);
        assertThat(SecureTokens.sha256(rawToken)).isEqualTo(hash.getValue());
    }

    @Test
    void confirm_validToken_verifiesAndSignsIn() {
        User user = user(false);
        when(tokenStore.consumeOneTime(TokenPurpose.EMAIL_VERIFICATION, SecureTokens.sha256("tok")))
                .thenReturn(Optional.of(7L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        AuthResponse issued = AuthResponse.builder().token("jwt").build();
        when(refreshTokenService.issue(user)).thenReturn(issued);

        AuthResponse response = service.confirm("tok");

        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
        verify(userRepository).save(user);
        assertThat(response).isSameAs(issued);
    }

    @Test
    void confirm_invalidToken_isRejected() {
        when(tokenStore.consumeOneTime(TokenPurpose.EMAIL_VERIFICATION, SecureTokens.sha256("bad")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm("bad")).isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(userRepository, refreshTokenService);
    }

    @Test
    void resend_unverifiedUser_outsideCooldown_sendsAgain() {
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user(false)));
        when(tokenStore.tryStartCooldown(TokenPurpose.EMAIL_VERIFICATION, 7L, Duration.ofSeconds(60))).thenReturn(true);

        service.resend("jane@example.com");

        verify(events).publishEvent(any(AccountEmailEventRelay.Outgoing.class));
    }

    @Test
    void resend_withinCooldown_sendsNothing() {
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user(false)));
        when(tokenStore.tryStartCooldown(TokenPurpose.EMAIL_VERIFICATION, 7L, Duration.ofSeconds(60))).thenReturn(false);

        service.resend("jane@example.com");

        verify(events, never()).publishEvent(any());
    }

    @Test
    void resend_alreadyVerifiedOrUnknown_sendsNothing() {
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user(true)));
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        service.resend("jane@example.com");
        service.resend("ghost@example.com");

        verify(events, never()).publishEvent(any());
        verify(tokenStore, never()).tryStartCooldown(any(), any(), any());
    }
}
