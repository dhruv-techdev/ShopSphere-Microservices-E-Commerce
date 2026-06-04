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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

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
                .build();

        User saved = userRepository.save(user);
        String token = jwtUtil.generateToken(saved.getEmail(), saved.getId(), saved.getRole().name());

        return toAuthResponse(saved, token);
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

        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole().name());
        return toAuthResponse(user, token);
    }

    private AuthResponse toAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .token(token)
                .build();
    }
}
