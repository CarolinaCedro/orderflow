# Skill: Publish Kafka Event

## Purpose

Correctly publish a Kafka event using `KafkaTemplate<String, String>` with proper logging,
topic constant, and key selection.

## When to Use

- Publishing any event from any service in OrderFlow.
- Adding a new Kafka producer to an existing or new service.
- Publishing enriched JSON payloads (vs. plain orderId).

## Prerequisites

- Kafka running at `localhost:9091`.
- Topic exists (either via Docker Compose `KAFKA_CREATE_TOPICS` or manually created).
- `KafkaTemplate<String, String>` is configured (either via `application.yml` or explicit `@Bean`).

## Knowledge References

- `.agents/knowledge/spring/kafka.md` — KafkaTemplate usage and config
- `.agents/rules/coding-standards.md` — logging standards for Kafka producers

---

## Steps

### Step 1: Configure KafkaTemplate via application.yml

The simplest approach — Spring Boot autoconfigures `KafkaTemplate` from these properties:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9091
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

### Step 2: Inject KafkaTemplate via Constructor

```java
@Service
public class OrderServiceImpl extends AbstractService<Order> implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final String VENDAS_TOPICO = "vendas-topico";  // Constant, never inline

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderServiceImpl(OrderRepository orderRepository,
                            KafkaTemplate<String, String> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }
}
```

### Step 3: Publish with Key for Ordering Guarantees

Always use `kafkaTemplate.send(topic, key, value)` — the key determines partition assignment.
Using the same key (orderId) guarantees all events for the same order go to the same partition,
preserving message ordering.

```java
// Publish plain string (orderId only — current pattern for vendas-topico)
log.info("Publishing order {} to {}", orderId, VENDAS_TOPICO);
kafkaTemplate.send(VENDAS_TOPICO, orderId, orderId);  // key=orderId, value=orderId
log.info("Order {} published to {}", orderId, VENDAS_TOPICO);
```

### Step 4: Publish JSON Payload (payment-processed pattern)

```java
private static final String PAYMENT_PROCESSED_TOPIC = "payment-processed";

private void publishPaymentResult(String orderId, String status) {
    String payload = """
        {"orderId":"%s","status":"%s","processedAt":"%s"}
        """.strip().formatted(orderId, status, Instant.now());

    log.info("Publishing payment result for order {} to {}: status={}",
             orderId, PAYMENT_PROCESSED_TOPIC, status);
    kafkaTemplate.send(PAYMENT_PROCESSED_TOPIC, orderId, payload);
    log.info("Payment result published for order {}", orderId);
}
```

### Step 5: Handle Send Callback (Optional — for confirmation logging)

```java
kafkaTemplate.send(VENDAS_TOPICO, orderId, orderId)
    .whenComplete((result, ex) -> {
        if (ex == null) {
            log.info("Order {} delivered to partition {} at offset {}",
                     orderId,
                     result.getRecordMetadata().partition(),
                     result.getRecordMetadata().offset());
        } else {
            log.error("Failed to deliver order {} to {}", orderId, VENDAS_TOPICO, ex);
        }
    });
```

Use this when delivery confirmation matters. For `order-service`'s `vendas-topico` publish,
fire-and-forget is acceptable — the order is already persisted in MongoDB.

### Step 6: Adding a New Topic

1. Add topic to Docker Compose `KAFKA_CREATE_TOPICS`:
   ```yaml
   KAFKA_CREATE_TOPICS: "vendas-topico:1:1,payment-processed:1:1,inventory-reserved:1:1"
   ```
2. Define topic constant in the producer service:
   ```java
   private static final String INVENTORY_RESERVED_TOPIC = "inventory-reserved";
   ```
3. Use `kafkaTemplate.send(INVENTORY_RESERVED_TOPIC, key, payload)`.
4. Create consumers with unique group IDs in consuming services.

---

## Validation Checklist

- [ ] Topic name defined as `private static final String` constant
- [ ] `KafkaTemplate<String, String>` injected via constructor
- [ ] Log statement BEFORE `kafkaTemplate.send()`
- [ ] Log statement AFTER `kafkaTemplate.send()`
- [ ] Key is the entity ID (orderId, productId, etc.) — NOT null
- [ ] `bootstrap-servers: localhost:9091` in `application.yml`
- [ ] String serializers configured for key and value

## Common Mistakes

- Hardcoding topic name as a string literal — use constants.
- Calling `kafkaTemplate.send(topic, value)` without a key — breaks ordering and partitioning.
- Logging only after send — if send throws, you lose the pre-send context.
- Using `@Autowired` on `kafkaTemplate` field — use constructor injection.
- Forgetting to add the topic to Docker Compose — consumer gets `UnknownTopicOrPartitionException`.
