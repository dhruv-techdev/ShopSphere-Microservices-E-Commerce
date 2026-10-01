package com.shopsphere.orderservice.config;

import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.common.events.ReservationExpiredEvent;
import com.shopsphere.common.events.ShipmentDeliveredEvent;
import com.shopsphere.common.events.ShipmentDispatchedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * order-service consumers: shipment events (US36), reservation expiry (US38)
 * and payment outcomes (US45).
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private <T> Map<String, Object> baseProps(Class<T> targetType) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.shopsphere.common.events");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, targetType.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return props;
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> factory(Class<T> type) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, T>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(baseProps(type)));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(3);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ShipmentDispatchedEvent>
            shipmentDispatchedListenerContainerFactory() {
        return factory(ShipmentDispatchedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ShipmentDeliveredEvent>
            shipmentDeliveredListenerContainerFactory() {
        return factory(ShipmentDeliveredEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReservationExpiredEvent>
            reservationExpiredListenerContainerFactory() {
        return factory(ReservationExpiredEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentSuccessfulEvent>
            paymentSuccessfulListenerContainerFactory() {
        return factory(PaymentSuccessfulEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent>
            paymentFailedListenerContainerFactory() {
        return factory(PaymentFailedEvent.class);
    }
}
