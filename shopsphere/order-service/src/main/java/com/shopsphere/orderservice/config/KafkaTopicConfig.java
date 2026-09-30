package com.shopsphere.orderservice.config;

import com.shopsphere.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics are declared programmatically here so that order-service ensures they exist on boot.
 * In production this would be done via infrastructure (Terraform, etc), but for the project
 * Spring's KafkaAdmin will create any missing topics at startup.
 * Everything order-scoped is keyed by orderId, so per-order events stay ordered.
 */
@Configuration
public class KafkaTopicConfig {

    private static NewTopic orderScoped(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCreatedTopic() {
        return orderScoped(Topics.ORDER_CREATED);
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return orderScoped(Topics.ORDER_CANCELLED);
    }

    @Bean
    public NewTopic paymentSuccessfulTopic() {
        return orderScoped(Topics.PAYMENT_SUCCESSFUL);
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return orderScoped(Topics.PAYMENT_FAILED);
    }

    @Bean
    public NewTopic lowStockTopic() {
        return TopicBuilder.name(Topics.LOW_STOCK)
                .partitions(1)   // low-stock is low-volume, single partition is fine
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reservationExpiredTopic() {
        return orderScoped(Topics.RESERVATION_EXPIRED);
    }

    @Bean
    public NewTopic shipmentDispatchedTopic() {
        return orderScoped(Topics.SHIPMENT_DISPATCHED);
    }

    @Bean
    public NewTopic shipmentDeliveredTopic() {
        return orderScoped(Topics.SHIPMENT_DELIVERED);
    }
}
