package com.shopsphere.orderservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.common.events.OrderItemSnapshot;
import com.shopsphere.common.events.ReservationExpiredEvent;
import com.shopsphere.common.events.Topics;
import com.shopsphere.orderservice.entity.CancellationReason;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderItem;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.repository.ProcessedEventRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * US38 — compensation path through the real order-service:
 *   inventory.reservation-expired (Kafka) → listener → Flyway-managed Postgres (CANCELLED + reason)
 *   → AFTER_COMMIT → order.cancelled (Kafka).
 *
 * Postgres via Testcontainers (skipped when Docker isn't available), Kafka embedded in-JVM.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 1, topics = {Topics.RESERVATION_EXPIRED, Topics.ORDER_CANCELLED})
@Testcontainers(disabledWithoutDocker = true)
class ReservationExpiryCompensationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OrderRepository orderRepository;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired EmbeddedKafkaBroker embeddedKafka;
    @Autowired ObjectMapper objectMapper;

    private Consumer<String, String> cancelledConsumer;
    private final List<JsonNode> cancelledEvents = new ArrayList<>();

    @BeforeEach
    void subscribeToOrderCancelled() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("it-" + UUID.randomUUID(), "true", embeddedKafka);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cancelledConsumer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(cancelledConsumer, Topics.ORDER_CANCELLED);
        cancelledEvents.clear();
    }

    @AfterEach
    void closeConsumer() {
        cancelledConsumer.close();
    }

    @Test
    void expiredReservation_cancelsPendingOrder_andPublishesOrderCancelledOnce() throws Exception {
        Order order = orderRepository.save(order(OrderStatus.PENDING_PAYMENT));
        ReservationExpiredEvent expired = expiredEvent(order.getId());

        kafkaTemplate.send(Topics.RESERVATION_EXPIRED, key(order), expired).get(10, TimeUnit.SECONDS);

        // 1. Order is compensated in the database
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(reloaded.getCancellationReason()).isEqualTo(CancellationReason.RESERVATION_EXPIRED);
            assertThat(reloaded.getCancelledAt()).isNotNull();
        });

        // 2. order.cancelled goes out with the reason
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            drain(Duration.ofMillis(500));
            assertThat(cancelledFor(order.getId())).hasSize(1);
        });
        JsonNode cancelled = cancelledFor(order.getId()).get(0);
        assertThat(cancelled.get("eventType").asText()).isEqualTo("order.cancelled.v1");
        assertThat(cancelled.get("userId").asLong()).isEqualTo(7L);
        assertThat(cancelled.get("reason").asText()).isEqualTo("RESERVATION_EXPIRED");
        assertThat(cancelled.get("reasonDescription").asText()).isNotBlank();
        assertThat(cancelled.get("items")).hasSize(1);

        // 3. Redelivery of the same expiry is a no-op (idempotent)
        kafkaTemplate.send(Topics.RESERVATION_EXPIRED, key(order), expired).get(10, TimeUnit.SECONDS);
        drain(Duration.ofSeconds(5));
        assertThat(cancelledFor(order.getId())).hasSize(1);
    }

    @Test
    void expiredReservation_doesNotCancelAnOrderThatAlreadyShipped() throws Exception {
        Order order = orderRepository.save(order(OrderStatus.SHIPPED));
        ReservationExpiredEvent expired = expiredEvent(order.getId());

        kafkaTemplate.send(Topics.RESERVATION_EXPIRED, key(order), expired).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30))
                .until(() -> processedEventRepository.existsById(expired.getEventId()));

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(reloaded.getCancellationReason()).isNull();

        drain(Duration.ofSeconds(3));
        assertThat(cancelledFor(order.getId())).isEmpty();
    }

    /* ------------------------------ helpers ------------------------------ */

    private static String key(Order order) {
        return String.valueOf(order.getId());
    }

    private static Order order(OrderStatus status) {
        Order order = Order.builder()
                .userId(7L).status(status)
                .totalAmount(new BigDecimal("100.00")).itemCount(2)
                .build();
        order.addItem(OrderItem.builder()
                .productId(10L).productName("Keyboard")
                .unitPrice(new BigDecimal("50.00")).quantity(2)
                .lineTotal(new BigDecimal("100.00"))
                .build());
        return order;
    }

    private static ReservationExpiredEvent expiredEvent(Long orderId) {
        Instant now = Instant.now();
        return ReservationExpiredEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(ReservationExpiredEvent.TYPE)
                .occurredAt(now)
                .orderId(orderId)
                .expiredAt(now)
                .items(List.of(OrderItemSnapshot.builder().productId(10L).quantity(2).build()))
                .build();
    }

    private void drain(Duration window) throws Exception {
        for (ConsumerRecord<String, String> record : KafkaTestUtils.getRecords(cancelledConsumer, window)) {
            cancelledEvents.add(objectMapper.readTree(record.value()));
        }
    }

    private List<JsonNode> cancelledFor(Long orderId) {
        return cancelledEvents.stream()
                .filter(e -> e.path("orderId").asLong() == orderId)
                .toList();
    }
}
