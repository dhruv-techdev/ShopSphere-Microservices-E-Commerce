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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final AuthProperties authProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() == null ? Role.CUSTOMER : request.getRole())
                .enabled(Boolean.TRUE)
                .emailVerified(Boolean.FALSE)
                .build();

        User saved = userRepository.save(user);
        emailVerificationService.sendVerification(saved);   // emailed after commit

        if (authProperties.isRequireVerifiedEmail()) {
            return AuthResponse.builder()
                    .userId(saved.getId())
                    .email(saved.getEmail())
                    .firstName(saved.getFirstName())
                    .lastName(saved.getLastName())
                    .role(saved.getRole())
                    .emailVerified(Boolean.FALSE)
                    .message("Account created. Check your email to verify your address before logging in.")
                    .build();
        }
        return refreshTokenService.issue(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // Checked only after the password, so verification state isn't revealed to strangers.
        if (authProperties.isRequireVerifiedEmail() && !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new EmailNotVerifiedException();
        }

        return refreshTokenService.issue(user);
    }

    public AuthResponse refresh(String refreshToken) {
        return refreshTokenService.rotate(refreshToken);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }
}
