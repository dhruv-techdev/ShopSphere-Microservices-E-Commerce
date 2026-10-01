package com.shopsphere.cartservice.model;

import com.shopsphere.cartservice.config.RedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A cart written to Redis (including computed total/itemCount) must read back. */
class CartRedisSerializationTest {

    private final Jackson2JsonRedisSerializer<Cart> serializer =
            new Jackson2JsonRedisSerializer<>(new RedisConfig().redisObjectMapper(), Cart.class);

    @Test
    void cartRoundTripsThroughRedisSerializer() {
        Cart cart = Cart.builder()
                .userId(7L)
                .items(List.of(CartItem.builder()
                        .productId(10L).name("Mug").unitPrice(new BigDecimal("12.50")).quantity(2)
                        .build()))
                .createdAt(Instant.parse("2026-10-01T10:00:00Z"))
                .updatedAt(Instant.parse("2026-10-01T10:05:00Z"))
                .build();

        byte[] stored = serializer.serialize(cart);
        assertThat(new String(stored, StandardCharsets.UTF_8)).contains("\"total\"").contains("\"itemCount\"");

        Cart restored = serializer.deserialize(stored);

        assertThat(restored.getUserId()).isEqualTo(7L);
        assertThat(restored.getItems()).hasSize(1);
        assertThat(restored.getTotal()).isEqualByComparingTo("25.00");
        assertThat(restored.getItemCount()).isEqualTo(2);
        assertThat(restored.getUpdatedAt()).isEqualTo(Instant.parse("2026-10-01T10:05:00Z"));
    }
}
