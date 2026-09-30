package com.shopsphere.userservice.service;

import com.shopsphere.userservice.auth.AuthTokenStore;
import com.shopsphere.userservice.auth.RefreshSession;
import com.shopsphere.userservice.auth.SecureTokens;
import com.shopsphere.userservice.config.AuthProperties;
import com.shopsphere.userservice.dto.AuthResponse;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.InvalidRefreshTokenException;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Duration TTL = Duration.ofDays(14);

    @Mock UserRepository userRepository;
    @Mock AuthTokenStore tokenStore;

    private final JwtUtil jwtUtil = new JwtUtil("x".repeat(64), 900_000);
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties();
        props.setRefreshTokenTtl(TTL);
        service = new RefreshTokenService(userRepository, jwtUtil, tokenStore, props);
    }

    private static User user(boolean enabled) {
        return User.builder().id(7L).email("jane@example.com").firstName("Jane").lastName("Doe")
                .role(Role.CUSTOMER).enabled(enabled).emailVerified(true).build();
    }

    @Test
    void issue_returnsAccessAndRefresh_andStoresOnlyTheHash() {
        AuthResponse response = service.issue(user(true));

        assertThat(response.getToken()).isNotBlank();
        assertThat(jwtUtil.extractEmail(response.getToken())).isEqualTo("jane@example.com");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900);
        assertThat(response.getRefreshExpiresIn()).isEqualTo(TTL.toSeconds());

        ArgumentCaptor<String> storedHash = ArgumentCaptor.forClass(String.class);
        verify(tokenStore).storeRefresh(storedHash.capture(), eq(7L), anyString(), eq(TTL));
        assertThat(storedHash.getValue())
                .isEqualTo(SecureTokens.sha256(response.getRefreshToken()))
                .isNotEqualTo(response.getRefreshToken());
    }

    @Test
    void rotate_validToken_consumesIt_andIssuesNewPairInSameFamily() {
        String presented = "presented-token";
        String hash = SecureTokens.sha256(presented);
        when(tokenStore.consumeRefresh(hash)).thenReturn(Optional.of(new RefreshSession(7L, "fam-1")));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(true)));

        AuthResponse response = service.rotate(presented);

        verify(tokenStore).markUsed(hash, "fam-1", TTL);
        verify(tokenStore).storeRefresh(eq(SecureTokens.sha256(response.getRefreshToken())), eq(7L), eq("fam-1"), eq(TTL));
        assertThat(response.getRefreshToken()).isNotEqualTo(presented);
    }

    @Test
    void rotate_reusedToken_revokesWholeFamily() {
        String hash = SecureTokens.sha256("stolen-copy");
        when(tokenStore.consumeRefresh(hash)).thenReturn(Optional.empty());
        when(tokenStore.familyOfUsed(hash)).thenReturn(Optional.of("fam-1"));

        assertThatThrownBy(() -> service.rotate("stolen-copy")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(tokenStore).revokeFamily("fam-1");
    }

    @Test
    void rotate_unknownToken_isRejected_withoutRevoking() {
        String hash = SecureTokens.sha256("garbage");
        when(tokenStore.consumeRefresh(hash)).thenReturn(Optional.empty());
        when(tokenStore.familyOfUsed(hash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("garbage")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(tokenStore, never()).revokeFamily(any());
    }

    @Test
    void rotate_disabledUser_revokesSession() {
        String hash = SecureTokens.sha256("t");
        when(tokenStore.consumeRefresh(hash)).thenReturn(Optional.of(new RefreshSession(7L, "fam-1")));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> service.rotate("t")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(tokenStore).revokeFamily("fam-1");
        verify(tokenStore, never()).storeRefresh(any(), any(), any(), any());
    }

    @Test
    void revoke_endsTheSession() {
        String hash = SecureTokens.sha256("t");
        when(tokenStore.consumeRefresh(hash)).thenReturn(Optional.of(new RefreshSession(7L, "fam-1")));

        service.revoke("t");

        verify(tokenStore).revokeFamily("fam-1");
    }
}
