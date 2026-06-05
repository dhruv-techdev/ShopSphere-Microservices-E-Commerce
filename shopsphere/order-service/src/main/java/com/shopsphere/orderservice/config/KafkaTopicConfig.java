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
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(Topics.ORDER_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentSuccessfulTopic() {
        return TopicBuilder.name(Topics.PAYMENT_SUCCESSFUL)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(Topics.PAYMENT_FAILED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic lowStockTopic() {
        return TopicBuilder.name(Topics.LOW_STOCK)
                .partitions(1)   // low-stock is low-volume, single partition is fine
                .replicas(1)
                .build();
    }
}
