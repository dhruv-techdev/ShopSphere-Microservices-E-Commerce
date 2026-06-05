package com.shopsphere.cartservice.repository;

import com.shopsphere.cartservice.model.Cart;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class CartRepository {

    private static final String KEY_PREFIX = "cart:";

    private final RedisTemplate<String, Cart> redisTemplate;
    private final Duration ttl;

    public CartRepository(RedisTemplate<String, Cart> redisTemplate,
                         @Value("${app.cart.ttl-hours}") long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public Optional<Cart> findByUserId(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    public Cart save(Cart cart) {
        redisTemplate.opsForValue().set(key(cart.getUserId()), cart, ttl);
        return cart;
    }

    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    public boolean exists(Long userId) {
        Boolean has = redisTemplate.hasKey(key(userId));
        return Boolean.TRUE.equals(has);
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
