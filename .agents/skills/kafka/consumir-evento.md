# Skill: Consume Kafka Event

## Purpose

Correctly implement a `@KafkaListener` method with proper error handling, logging, payload
parsing, and consumer factory configuration.

## When to Use

- Adding a new Kafka consumer to any service.
- Adding a consumer to `payment-processed` or `vendas-topico`.
- Debugging a consumer that is not receiving messages or is crashing.

## Prerequisites

- Kafka running at `localhost:9091`.
- Topic exists.
- Unique group ID chosen (distinct from all other consumers of the same topic).

## Knowledge References

- `.agents/knowledge/spring/kafka.md` — full consumer config and factory examples
- `.agents/rules/error-handling.md` — never rethrow in listeners
- `.agents/rules/coding-standards.md` — logging standards

---

## Steps

### Step 1: Configure Consumer via application.yml

Default consumer (no custom factory needed):
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9091
    consumer:
      group-id: my-service-group   # Unique group ID for this service
      auto-offset-reset: earliest  # Start from beginning for new groups
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

### Step 2: Simple @KafkaListener (using default factory)

When `containerFactory` is NOT specified, Spring uses `kafkaListenerContainerFactory` (the
default, autoconfigured from `application.yml`):

```java
@KafkaListener(topics = "vendas-topico", groupId = "payment-group")
public void processarVenda(String orderId) {
    log.info("Order received for payment processing: orderId={}", orderId);
    try {
        // process...
        log.info("Order {} processed successfully", orderId);
    } catch (Exception e) {
        log.error("Failed to process order {}. Will skip.", orderId, e);
        // Do NOT rethrow
    }
}
```

### Step 3: @KafkaListener with Custom Container Factory

When a service needs a different configuration than the default:

```java
@KafkaListener(topics = "payment-processed",
               containerFactory = "orderPaymentListenerContainerFactory")
public void onPaymentProcessed(String payload) {
    log.info("Payment event received. Payload: {}", payload);
    try {
        // process...
    } catch (Exception e) {
        log.error("Failed to process payment event. Payload: {}", payload, e);
    }
}
```

Declare the factory bean:
```java
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9091}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> orderPaymentConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "order-group");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
            orderPaymentListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(orderPaymentConsumerFactory());
        return factory;
    }
}
```

### Step 4: JSON Payload Parsing Pattern

```java
@KafkaListener(topics = "payment-processed", groupId = "notification-group")
public void listenPaymentStatus(String payload) {
    log.info("Payment notification received. Payload: {}", payload);
    try {
        JsonNode node = objectMapper.readTree(payload);

        String orderId = node.path("orderId").asText();
        String status = node.path("status").asText();

        // Validate extracted fields
        if (orderId.isBlank() || status.isBlank()) {
            log.error("Invalid payload — missing orderId or status. Payload: {}", payload);
            return;  // Skip this message
        }

        // Process...
        log.info("Processed notification for order {}, status={}", orderId, status);

    } catch (JsonProcessingException e) {
        log.error("Malformed JSON in payment-processed. Skipping. Payload: {}", payload, e);
    } catch (Exception e) {
        log.error("Unexpected error processing payment notification. Payload: {}", payload, e);
    }
    // Never rethrow — any exception ends here
}
```

### Step 5: Verify Consumer is Working

1. Check Kafdrop consumer groups at `http://localhost:9000/consumer-groups`.
2. Find the group (e.g., `notification-group`).
3. Check lag — `0` means messages have been consumed.
4. Check assigned partitions — if none, the consumer may not have started.

Via Kafka CLI:
```bash
docker exec -it kafka1 kafka-consumer-groups.sh \
  --bootstrap-server kafka1:19091 \
  --describe \
  --group notification-group
```

### Step 6: Adding a New Consumer to an Existing Topic

To add a new service (e.g., `erp-service`) consuming `payment-processed`:

1. New group ID: `erp-group` (never reuse an existing group ID).
2. Add to `application.yml`:
   ```yaml
   spring.kafka.consumer.group-id: erp-group
   ```
3. Implement `@KafkaListener`:
   ```java
   @KafkaListener(topics = "payment-processed", groupId = "erp-group")
   public void syncToErp(String payload) {
       log.info("ERP sync event received. Payload: {}", payload);
       try {
           // parse and forward to ERP
       } catch (Exception e) {
           log.error("ERP sync failed. Payload: {}", payload, e);
       }
   }
   ```
4. No changes to `payment-service` (the producer) required.

---

## Validation Checklist

- [ ] `groupId` is unique across all consumers of the same topic
- [ ] `bootstrap-servers: localhost:9091` (not 9092)
- [ ] `StringDeserializer` for both key and value (unless using JsonDeserializer explicitly)
- [ ] `auto-offset-reset: earliest` for new groups
- [ ] Log statement BEFORE processing
- [ ] ALL exceptions caught within the listener method
- [ ] No exception rethrown from the listener method
- [ ] If using custom `containerFactory`, the bean is declared in a `@Configuration` class

## Common Mistakes

- Reusing an existing group ID — messages go to only one service, breaking fan-out.
- Not catching exceptions — Spring Kafka retries indefinitely on uncaught exceptions.
- Using `JsonDeserializer` without `trusted.packages` config — `ClassNotFoundException` in consumer.
- Using wrong `bootstrap-servers` port (9092 instead of 9091) — connection refused, no error message.
- Checking `node.get("field")` instead of `node.path("field")` — `get` returns `null`, causing NPE.
  Always use `node.path("field").asText()` which returns an empty string if absent.
