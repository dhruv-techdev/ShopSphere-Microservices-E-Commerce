package com.shopsphere.userservice.auth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** US41 — token store against a real Redis (skipped when Docker isn't available). */
@Testcontainers(disabledWithoutDocker = true)
class AuthTokenStoreRedisTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate template;

    private AuthTokenStore store;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        template = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clean() {
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
        store = new AuthTokenStore(template);
    }

    @Test
    void refreshToken_canBeConsumedOnlyOnce_andHasTtl() {
        store.storeRefresh("h1", 7L, "fam-1", TTL);

        assertThat(template.getExpire(AuthTokenStore.RT + "h1")).isPositive();
        assertThat(store.consumeRefresh("h1")).contains(new RefreshSession(7L, "fam-1"));
        assertThat(store.consumeRefresh("h1")).isEmpty();
    }

    @Test
    void reuseOfRotatedToken_canRevokeTheCurrentTokenOfTheFamily() {
        store.storeRefresh("h1", 7L, "fam-1", TTL);
        store.consumeRefresh("h1");
        store.markUsed("h1", "fam-1", TTL);
        store.storeRefresh("h2", 7L, "fam-1", TTL);           // rotated

        assertThat(store.familyOfUsed("h1")).contains("fam-1"); // replay detected …
        store.revokeFamily("fam-1");                           // … so the live token dies too

        assertThat(store.consumeRefresh("h2")).isEmpty();
    }

    @Test
    void revokeAllForUser_endsEverySession() {
        store.storeRefresh("a", 7L, "fam-a", TTL);
        store.storeRefresh("b", 7L, "fam-b", TTL);
        store.storeRefresh("other", 8L, "fam-o", TTL);

        store.revokeAllForUser(7L);

        assertThat(store.consumeRefresh("a")).isEmpty();
        assertThat(store.consumeRefresh("b")).isEmpty();
        assertThat(store.consumeRefresh("other")).isPresent();   // other users untouched
    }

    @Test
    void oneTimeToken_newerRequestInvalidatesOlder_andEachIsSingleUse() {
        store.storeOneTime(TokenPurpose.PASSWORD_RESET, "old", 7L, TTL);
        store.storeOneTime(TokenPurpose.PASSWORD_RESET, "new", 7L, TTL);

        assertThat(store.consumeOneTime(TokenPurpose.PASSWORD_RESET, "old")).isEmpty();
        assertThat(store.consumeOneTime(TokenPurpose.PASSWORD_RESET, "new")).contains(7L);
        assertThat(store.consumeOneTime(TokenPurpose.PASSWORD_RESET, "new")).isEmpty();
    }

    @Test
    void oneTimeTokens_areScopedByPurpose() {
        store.storeOneTime(TokenPurpose.EMAIL_VERIFICATION, "tok", 7L, TTL);

        assertThat(store.consumeOneTime(TokenPurpose.PASSWORD_RESET, "tok")).isEmpty();
        assertThat(store.consumeOneTime(TokenPurpose.EMAIL_VERIFICATION, "tok")).contains(7L);
    }

    @Test
    void cooldown_allowsFirstRequestOnly() {
        assertThat(store.tryStartCooldown(TokenPurpose.PASSWORD_RESET, 7L, TTL)).isTrue();
        assertThat(store.tryStartCooldown(TokenPurpose.PASSWORD_RESET, 7L, TTL)).isFalse();
        assertThat(store.tryStartCooldown(TokenPurpose.EMAIL_VERIFICATION, 7L, TTL)).isTrue();
    }
}
