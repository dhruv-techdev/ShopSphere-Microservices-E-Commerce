package com.shopsphere.orderservice.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void forwardShippingPath_isAllowed() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void backwardsMoves_areRejected() {
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED", "PAYMENT_FAILED"})
    void terminalStates_neverMove(OrderStatus terminal) {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(terminal.canTransitionTo(target)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void sameState_isNotATransition(OrderStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }
}
