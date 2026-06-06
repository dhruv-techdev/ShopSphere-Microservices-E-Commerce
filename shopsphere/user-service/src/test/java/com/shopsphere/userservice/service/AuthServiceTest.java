package com.shopsphere.userservice.service;

import com.shopsphere.userservice.dto.AuthResponse;
import com.shopsphere.userservice.dto.LoginRequest;
import com.shopsphere.userservice.dto.RegisterRequest;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.DuplicateEmailException;
import com.shopsphere.userservice.exception.InvalidCredentialsException;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;

    @InjectMocks AuthService authService;

    @Test
    void register_duplicateEmail_throws() {
        when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

        RegisterRequest req = RegisterRequest.builder()
                .firstName("A").lastName("B")
                .email("dup@example.com").password("secret123")
                .build();

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void register_succeeds_andReturnsToken() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });
        when(jwtUtil.generateToken(anyString(), eq(42L), anyString())).thenReturn("jwt-token");

        RegisterRequest req = RegisterRequest.builder()
                .firstName("A").lastName("B")
                .email("New@example.com").password("secret123")
                .build();

        AuthResponse response = authService.register(req);

        assertThat(response.getUserId()).isEqualTo(42L);
        assertThat(response.getEmail()).isEqualTo("new@example.com");  // normalized
        assertThat(response.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(response.getToken()).isEqualTo("jwt-token");
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        User user = User.builder()
                .id(1L).email("a@b.com").passwordHash("hash")
                .role(Role.CUSTOMER).enabled(true)
                .build();
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        LoginRequest req = new LoginRequest("a@b.com", "wrong");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest("ghost@example.com", "any");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
