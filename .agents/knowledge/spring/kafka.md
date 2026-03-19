# Spring Kafka — OrderFlow Reference

Kafka broker: `localhost:9091`. Topics: `vendas-topico`, `payment-processed`.
Docker Compose: `@docker-compose/kafka/docker-compose.yml`.

---

## Topic Reference

| Topic               | Producer           | Consumers                                      | Message format        |
|---------------------|--------------------|------------------------------------------------|-----------------------|
| `vendas-topico`     | `order-service`    | `payment-service` (group: `payment-group`)     | key=orderId, value=orderId (plain string) |
| `payment-processed` | `payment-service`  | `order-service` (group: `order-group`), `notification-service` (group: `notification-group`) | key=orderId, value=JSON string |

---

## KafkaTemplate (Producer)

Configuration via `application.yml`:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9091
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

Usage in `OrderServiceImpl`:

```java
private static final String VENDAS_TOPICO = "vendas-topico";

private final KafkaTemplate<String, String> kafkaTemplate;

@Override
public ResponseEntity<Order> save(Order order, String returnEntity) {
    Order saved = orderRepository.save(order);
    String orderId = saved.getId();

    log.info("Publishing order {} to {}", orderId, VENDAS_TOPICO);
    kafkaTemplate.send(VENDAS_TOPICO, orderId, orderId);  // key=orderId, value=orderId
    log.info("Order {} published to {}", orderId, VENDAS_TOPICO);

    return ResponseEntity.ok(saved);
}
```

`KafkaTemplate<String, String>` is autoconfigured by Spring Boot when `spring-kafka` is on the
classpath and producer serializers are configured. No explicit `@Bean` needed for the basic case.

---

## @KafkaListener (Consumer)

### Simple string consumer (payment-service)

```yaml
spring:
  kafka:
    consumer:
      group-id: payment-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

```java
@KafkaListener(topics = "vendas-topico", groupId = "payment-group")
public void processarVenda(String orderId) {
    log.info("Order received for payment processing: orderId={}", orderId);
    String status = simulatePayment(orderId);
    String payload = "{\"orderId\":\"%s\",\"status\":\"%s\",\"processedAt\":\"%s\"}"
        .formatted(orderId, status, Instant.now());
    kafkaTemplate.send(PAYMENT_PROCESSED_TOPIC, orderId, payload);
    log.info("Payment processed: orderId={}, status={}", orderId, status);
}
```

### JSON consumer with manual parsing (order-service OrderPaymentListener)

```java
// Uses a separate container factory for the payment-processed topic
@KafkaListener(topics = "payment-processed",
               containerFactory = "orderPaymentListenerContainerFactory")
public void onPaymentProcessed(String payload) {
    try {
        JsonNode node = objectMapper.readTree(payload);
        String orderId = node.path("orderId").asText();
        String status = node.path("status").asText();

        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            if ("PAYMENT_SUCCESS".equals(status)) {
                order.setStatus(OrderStatus.COMPLETED);
                order.setApprovalStatus(ApprovalStatus.APPROVED);
                order.setApprovedBy("payment-service");
                order.setApprovalDate(LocalDateTime.now());
                log.info("Order {} completed successfully", orderId);
            } else {
                order.setStatus(OrderStatus.CANCELLED);
                order.setApprovalStatus(ApprovalStatus.REJECTED);
                log.warn("Order {} cancelled due to payment failure: {}", orderId, status);
            }
            orderRepository.save(order);
        }, () -> log.error("Order {} not found for payment update", orderId));

    } catch (Exception e) {
        log.error("Failed to process payment event. Payload: {}", payload, e);
        // Do NOT rethrow
    }
}
```

---

## Consumer Factory Configuration

When a listener uses a non-default container factory, declare it as a `@Bean`:

```java
// In order-service KafkaConsumerConfig (if present) or inline in @Configuration
@Bean
public ConsumerFactory<String, String> orderPaymentConsumerFactory() {
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9091");
    config.put(ConsumerConfig.GROUP_ID_CONFIG, "order-group");
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(config);
}

@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> orderPaymentListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(orderPaymentConsumerFactory());
    return factory;
}
```

If no `containerFactory` is specified in `@KafkaListener`, Spring uses the default factory named
`kafkaListenerContainerFactory` — autoconfigured from `application.yml` consumer settings.

---

## StringDeserializer vs JsonDeserializer

| Scenario                                                       | Deserializer                    |
|----------------------------------------------------------------|---------------------------------|
| Simple string messages (orderId only)                          | `StringDeserializer`            |
| JSON strings parsed manually with `ObjectMapper`               | `StringDeserializer`            |
| Automatic JSON deserialization to a Java class                  | `JsonDeserializer`              |

`payment-service` and `notification-service` receive JSON strings but parse them manually.
This is fine and avoids type safety issues with `JsonDeserializer`'s trusted packages config.

If using `JsonDeserializer`, always configure trusted packages:
```yaml
spring:
  kafka:
    consumer:
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring:
          json:
            trusted:
              packages: "org.cedro"
```

---

## Group IDs and Consumer Isolation

Multiple services can consume the same topic if they have different group IDs:

```
payment-processed topic
    ├── order-service    (group: order-group)       → receives a copy
    └── notification-service (group: notification-group) → receives a copy
```

Each group maintains its own offset. Both services receive every message independently.

If two instances of the same service run with the same group ID, Kafka load-balances
messages between them (each message goes to only one instance in the group).

---

## Error Handling in Listeners

Never rethrow from `@KafkaListener`. Log and move on:

```java
@KafkaListener(topics = "payment-processed", groupId = "notification-group")
public void listenPaymentStatus(String payload) {
    try {
        // ... process payload ...
    } catch (JsonProcessingException e) {
        log.error("Malformed JSON in payment-processed. Payload: {}", payload, e);
        // Discard — bad message, no point retrying
    } catch (Exception e) {
        log.error("Unexpected error in notification listener. Payload: {}", payload, e);
        // Discard — investigate via logs
    }
}
```

For transient errors (e.g., DB temporarily unavailable), a dead-letter queue (DLQ) strategy should
be implemented. Currently not in scope but future work:

```java
// Future: route to DLQ on retryable errors
@Bean
public DeadLetterPublishingRecoverer recoverer(KafkaTemplate<?, ?> template) {
    return new DeadLetterPublishingRecoverer(template,
        (r, e) -> new TopicPartition(r.topic() + ".DLT", r.partition()));
}
```

---

## Kafka Monitor (KafkaEventStore)

The project references a `KafkaMonitorController` pattern for event auditing. If implementing:

```java
@Document(collection = "kafka_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KafkaEvent {
    @Id
    private String id;
    private String topic;
    private String key;
    private String payload;
    private LocalDateTime receivedAt;
    private String consumerGroup;
    private String status; // PROCESSED, FAILED
}
```

```java
// In a listener, save to MongoDB before processing
@KafkaListener(topics = "vendas-topico", groupId = "payment-group")
public void processarVenda(String orderId) {
    kafkaEventRepository.save(new KafkaEvent(
        null, "vendas-topico", orderId, orderId, LocalDateTime.now(), "payment-group", "PROCESSING"
    ));
    // ... process ...
}
```

---

## Common Issues

### Log flooding
If `logging.level.org.apache.kafka` is not set to `WARN` or `ERROR`, Kafka client logs are
extremely verbose. Always add:

```yaml
logging:
  level:
    org.apache.kafka: WARN
```

### Wrong bootstrap-servers port
Services must use `localhost:9091` (not the default 9092). The Docker Compose file maps
Kafka's external listener to port 9091.

### Consumer not starting
Check that the topic exists. Kafka creates topics automatically if
`KAFKA_CREATE_TOPICS: "vendas-topico:1:1,payment-processed:1:1"` is set in the compose file.
Verify in Kafdrop at `http://localhost:9000`.
