# Skill: Process Payment via Kafka

## Purpose

Implement the payment processing flow: consume `vendas-topico`, simulate/execute payment,
publish result to `payment-processed`, and update the Order status in `order-service`.

## When to Use

- Modifying payment logic in `payment-service`.
- Adding real payment gateway integration (Stripe, PagSeguro, Cielo).
- Debugging payment not triggering or order status not updating after payment.
- Adding new payment status outcomes (PAYMENT_PENDING, PAYMENT_REFUNDED, etc.).

## Prerequisites

- Kafka running at `localhost:9091`.
- Topics `vendas-topico` and `payment-processed` exist.
- `order-service` is running and consuming `payment-processed`.

## Knowledge References

- `.agents/knowledge/orderflow/pagamento.md` — PaymentService full implementation
- `.agents/knowledge/spring/kafka.md` — @KafkaListener, KafkaTemplate, consumer factory
- `.agents/knowledge/architecture/event-driven.md` — full event flow diagram
- `.agents/rules/coding-standards.md` — logging before/after Kafka send

---

## Steps

### Step 1: Verify Kafka Consumer Config for payment-service

`payment-service/application.yml` must have:
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9091
    consumer:
      group-id: payment-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

### Step 2: Verify PaymentService @KafkaListener

```java
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String PAYMENT_PROCESSED_TOPIC = "payment-processed";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PaymentService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "vendas-topico", groupId = "payment-group")
    public void processarVenda(String orderId) {
        log.info("Order received for payment processing: orderId={}", orderId);  // Log BEFORE

        String status = simulatePayment(orderId);
        String payload = "{\"orderId\":\"%s\",\"status\":\"%s\",\"processedAt\":\"%s\"}"
            .formatted(orderId, status, java.time.Instant.now());

        kafkaTemplate.send(PAYMENT_PROCESSED_TOPIC, orderId, payload);
        log.info("Payment processed: orderId={}, status={}", orderId, status);  // Log AFTER
    }

    private String simulatePayment(String orderId) {
        log.info("Processing payment for order {}...", orderId);
        return "PAYMENT_SUCCESS";  // Simulate approval
    }
}
```

### Step 3: Verify OrderPaymentListener in order-service

```java
@Component
public class OrderPaymentListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentListener.class);

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderPaymentListener(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @KafkaListener(topics = "payment-processed",
                   containerFactory = "orderPaymentListenerContainerFactory")
    public void onPaymentProcessed(String payload) {
        log.info("Payment event received. Payload: {}", payload);  // Log BEFORE processing
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
                    log.info("Order {} marked COMPLETED", orderId);
                } else {
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setApprovalStatus(ApprovalStatus.REJECTED);
                    log.warn("Order {} marked CANCELLED. Payment status: {}", orderId, status);
                }
                orderRepository.save(order);
            }, () -> log.error("Order {} not found for payment update", orderId));

        } catch (Exception e) {
            log.error("Failed to process payment event. Payload: {}", payload, e);
            // Never rethrow
        }
    }
}
```

### Step 4: Declare the orderPaymentListenerContainerFactory Bean

In `order-service`, add a `@Configuration` class (or in existing Kafka config):

```java
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
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

### Step 5: Add simulatePaymentFailure for Testing

To test the failure path:

```java
private String simulatePayment(String orderId) {
    // Simulate failure for order IDs ending in "F"
    if (orderId.endsWith("F")) {
        log.warn("Simulating payment failure for order {}", orderId);
        return "PAYMENT_FAILURE";
    }
    return "PAYMENT_SUCCESS";
}
```

---

## Validation Checklist

- [ ] `payment-service` `@KafkaListener` has `groupId = "payment-group"`
- [ ] Log present BEFORE consuming the message
- [ ] Log present AFTER publishing to `payment-processed`
- [ ] `order-service` `OrderPaymentListener` has `containerFactory = "orderPaymentListenerContainerFactory"`
- [ ] `orderPaymentListenerContainerFactory` bean declared in a `@Configuration` class
- [ ] Exception in `onPaymentProcessed` is caught and NOT rethrown
- [ ] `order-service` group ID is `"order-group"` (distinct from `payment-group`)

## Common Mistakes

- Using the same group ID in both services — messages go to only one consumer.
- Missing `containerFactory` in `@KafkaListener` — uses default factory (may have wrong config).
- `orderPaymentListenerContainerFactory` bean not declared — Spring throws `NoSuchBeanDefinitionException`.
- Rethrowing from `@KafkaListener` — causes infinite retry loop.
- Updating order status before parsing JSON — if JSON parse fails, order gets wrong status.
