package com.shopsphere.userservice.service;

import com.shopsphere.userservice.config.AuthProperties;
import com.shopsphere.userservice.dto.AuthResponse;
import com.shopsphere.userservice.dto.LoginRequest;
import com.shopsphere.userservice.dto.RegisterRequest;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.DuplicateEmailException;
import com.shopsphere.userservice.exception.EmailNotVerifiedException;
import com.shopsphere.userservice.exception.InvalidCredentialsException;
import com.shopsphere.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RefreshTokenService refreshTokenService;
    @Mock EmailVerificationService emailVerificationService;

    private AuthProperties properties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setRequireVerifiedEmail(true);
        authService = new AuthService(userRepository, passwordEncoder, refreshTokenService,
                emailVerificationService, properties);
    }

    private static RegisterRequest registration(String email) {
        return RegisterRequest.builder()
                .firstName("A").lastName("B").email(email).password("secret123").build();
    }

    private void stubSave() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });
    }

    private static User user(boolean verified) {
        return User.builder()
                .id(1L).email("a@b.com").passwordHash("hash")
                .role(Role.CUSTOMER).enabled(true).emailVerified(verified)
                .build();
    }

    @Test
    void register_duplicateEmail_throws() {
        when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registration("dup@example.com")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void register_verificationRequired_createsUnverifiedUser_sendsEmail_returnsNoTokens() {
        stubSave();

        AuthResponse response = authService.register(registration("New@example.com"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmailVerified()).isFalse();
        assertThat(saved.getValue().getEmail()).isEqualTo("new@example.com");

        verify(emailVerificationService).sendVerification(saved.getValue());
        verify(refreshTokenService, never()).issue(any());
        assertThat(response.getToken()).isNull();
        assertThat(response.getRefreshToken()).isNull();
        assertThat(response.getEmailVerified()).isFalse();
        assertThat(response.getMessage()).contains("verify");
    }

    @Test
    void register_verificationNotRequired_returnsTokens() {
        properties.setRequireVerifiedEmail(false);
        stubSave();
        AuthResponse issued = AuthResponse.builder().token("jwt").refreshToken("rt").build();
        when(refreshTokenService.issue(any(User.class))).thenReturn(issued);

        AuthResponse response = authService.register(registration("new@example.com"));

        assertThat(response).isSameAs(issued);
        verify(emailVerificationService).sendVerification(any(User.class));
    }

    @Test
    void login_verifiedUser_getsTokenPair() {
        User user = user(true);
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
        AuthResponse issued = AuthResponse.builder().token("jwt").refreshToken("rt").build();
        when(refreshTokenService.issue(user)).thenReturn(issued);

        assertThat(authService.login(new LoginRequest("a@b.com", "secret123"))).isSameAs(issued);
    }

    @Test
    void login_unverifiedUser_rightPassword_isRefused() {
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user(false)));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "secret123")))
                .isInstanceOf(EmailNotVerifiedException.class);
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void login_unverifiedUser_wrongPassword_revealsNothingAboutVerification() {
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user(false)));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "any")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
