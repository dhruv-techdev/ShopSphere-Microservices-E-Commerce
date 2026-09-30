package com.shopsphere.userservice.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * US41 — all auth token state, in Redis with TTLs. Keys (values are hashes, never raw tokens):
 *
 * <pre>
 *   auth:rt:{hash}               → "userId|familyId"   live refresh token
 *   auth:rt-used:{hash}          → familyId            already-rotated token (reuse detection)
 *   auth:rt-family:{familyId}    → hash                the one live token of a session
 *   auth:rt-user:{userId}        → SET familyId        sessions per user (logout everywhere)
 *   auth:verify:{hash}           → userId              email-verification token
 *   auth:pwreset:{hash}          → userId              password-reset token
 *   auth:{purpose}:user:{userId} → hash                latest token per user (older ones die)
 *   auth:{purpose}:cooldown:{id} → "1"                 request throttle
 * </pre>
 *
 * Consumption uses GETDEL, so a token can be redeemed at most once even under concurrency.
 */
@Component
@RequiredArgsConstructor
public class AuthTokenStore {

    static final String RT = "auth:rt:";
    static final String RT_USED = "auth:rt-used:";
    static final String RT_FAMILY = "auth:rt-family:";
    static final String RT_USER = "auth:rt-user:";

    private final StringRedisTemplate redis;

    /* ------------------------- refresh tokens ------------------------- */

    public void storeRefresh(String tokenHash, Long userId, String familyId, Duration ttl) {
        redis.opsForValue().set(RT + tokenHash, userId + "|" + familyId, ttl);
        redis.opsForValue().set(RT_FAMILY + familyId, tokenHash, ttl);
        redis.opsForSet().add(RT_USER + userId, familyId);
        redis.expire(RT_USER + userId, ttl);
    }

    /** Atomically redeems a refresh token (GETDEL). */
    public Optional<RefreshSession> consumeRefresh(String tokenHash) {
        String value = redis.opsForValue().getAndDelete(RT + tokenHash);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", 2);
        return Optional.of(new RefreshSession(Long.valueOf(parts[0]), parts[1]));
    }

    /** Remember a rotated token so a replay can be recognised as theft. */
    public void markUsed(String tokenHash, String familyId, Duration ttl) {
        redis.opsForValue().set(RT_USED + tokenHash, familyId, ttl);
    }

    public Optional<String> familyOfUsed(String tokenHash) {
        return Optional.ofNullable(redis.opsForValue().get(RT_USED + tokenHash));
    }

    public void revokeFamily(String familyId) {
        String active = redis.opsForValue().getAndDelete(RT_FAMILY + familyId);
        if (active != null) {
            redis.delete(RT + active);
        }
    }

    public void revokeAllForUser(Long userId) {
        Set<String> families = redis.opsForSet().members(RT_USER + userId);
        if (families != null) {
            families.forEach(this::revokeFamily);
        }
        redis.delete(RT_USER + userId);
    }

    /* ----------------------- single-use tokens ----------------------- */

    /** Stores a new token for the user and invalidates the previous one of the same purpose. */
    public void storeOneTime(TokenPurpose purpose, String tokenHash, Long userId, Duration ttl) {
        String pointer = purpose.keyPrefix() + "user:" + userId;
        String previous = redis.opsForValue().getAndSet(pointer, tokenHash);
        redis.expire(pointer, ttl);
        if (previous != null && !previous.equals(tokenHash)) {
            redis.delete(purpose.keyPrefix() + previous);
        }
        redis.opsForValue().set(purpose.keyPrefix() + tokenHash, String.valueOf(userId), ttl);
    }

    /** Atomically redeems a single-use token (GETDEL). */
    public Optional<Long> consumeOneTime(TokenPurpose purpose, String tokenHash) {
        String value = redis.opsForValue().getAndDelete(purpose.keyPrefix() + tokenHash);
        if (value == null) {
            return Optional.empty();
        }
        Long userId = Long.valueOf(value);
        redis.delete(purpose.keyPrefix() + "user:" + userId);
        return Optional.of(userId);
    }

    /** @return true if no request of this purpose was made for the user within {@code cooldown}. */
    public boolean tryStartCooldown(TokenPurpose purpose, Long userId, Duration cooldown) {
        return Boolean.TRUE.equals(redis.opsForValue()
                .setIfAbsent(purpose.keyPrefix() + "cooldown:" + userId, "1", cooldown));
    }
}
