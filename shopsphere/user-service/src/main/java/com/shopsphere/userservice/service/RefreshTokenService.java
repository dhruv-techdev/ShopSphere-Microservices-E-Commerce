package com.shopsphere.userservice.service;

import com.shopsphere.userservice.auth.AuthTokenStore;
import com.shopsphere.userservice.auth.RefreshSession;
import com.shopsphere.userservice.auth.SecureTokens;
import com.shopsphere.userservice.config.AuthProperties;
import com.shopsphere.userservice.dto.AuthResponse;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.InvalidRefreshTokenException;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * US41 — issues access + refresh token pairs and rotates refresh tokens.
 *
 * <p>Rotation: every refresh consumes the presented token (GETDEL) and issues a new one in
 * the same family. Presenting an already-rotated token means it was copied — the whole family
 * (that login session) is revoked, logging out both the attacker and the victim.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final AuthTokenStore tokenStore;
    private final AuthProperties properties;

    /** New login session. */
    public AuthResponse issue(User user) {
        return issueInFamily(user, UUID.randomUUID().toString());
    }

    public AuthResponse rotate(String refreshToken) {
        String hash = SecureTokens.sha256(refreshToken);
        Optional<RefreshSession> session = tokenStore.consumeRefresh(hash);

        if (session.isEmpty()) {
            tokenStore.familyOfUsed(hash).ifPresent(familyId -> {
                log.warn("Refresh token reuse detected — revoking session family {}", familyId);
                tokenStore.revokeFamily(familyId);
            });
            throw new InvalidRefreshTokenException();
        }

        RefreshSession current = session.get();
        tokenStore.markUsed(hash, current.familyId(), properties.getRefreshTokenTtl());

        User user = userRepository.findById(current.userId())
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .orElse(null);
        if (user == null) {
            tokenStore.revokeFamily(current.familyId());
            throw new InvalidRefreshTokenException();
        }

        return issueInFamily(user, current.familyId());
    }

    /** Logout: ends the session the token belongs to. Unknown tokens are ignored. */
    public void revoke(String refreshToken) {
        tokenStore.consumeRefresh(SecureTokens.sha256(refreshToken))
                .ifPresent(session -> tokenStore.revokeFamily(session.familyId()));
    }

    /** Logout everywhere (e.g. after a password reset). */
    public void revokeAll(Long userId) {
        tokenStore.revokeAllForUser(userId);
    }

    private AuthResponse issueInFamily(User user, String familyId) {
        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole().name());
        String refreshToken = SecureTokens.generate();
        tokenStore.storeRefresh(SecureTokens.sha256(refreshToken), user.getId(), familyId,
                properties.getRefreshTokenTtl());

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .emailVerified(user.getEmailVerified())
                .token(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationMs() / 1000)
                .refreshToken(refreshToken)
                .refreshExpiresIn(properties.getRefreshTokenTtl().toSeconds())
                .build();
    }
}
