# Payment Flow — OrderFlow

`payment-service:8085` — Kafka consumer/producer, no REST endpoints, no database.

---

## Responsibility

`payment-service` does one thing: consume an `orderId` from `vendas-topico`, simulate (or
execute) payment, and publish the result to `payment-processed`.

It is stateless — no MongoDB, no JPA. All state lives in the Kafka messages it publishes.

---

## PaymentService Implementation

`org.cedro.paymentservice.service.PaymentService`

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
        log.info("Order received for payment processing: orderId={}", orderId);

        String status = simulatePayment(orderId);

        String payload = String.format(
            "{\"orderId\":\"%s\",\"status\":\"%s\",\"processedAt\":\"%s\"}",
            orderId, status, java.time.Instant.now()
        );

        kafkaTemplate.send(PAYMENT_PROCESSED_TOPIC, orderId, payload);
        log.info("Payment processed: orderId={}, status={}", orderId, status);
    }

    private String simulatePayment(String orderId) {
        log.info("Processing payment for order {}...", orderId);
        return "PAYMENT_SUCCESS";  // Always succeeds in simulation
    }
}
```

---

## Kafka Configuration (payment-service)

`application.yml`:
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

Note: `payment-service` uses `StringDeserializer` for both key and value. The message from
`vendas-topico` is a plain orderId string.

---

## Payment Result Payload

Published to `payment-processed`:
```json
{
    "orderId": "64c9f8a1b5e3f20012345678",
    "status": "PAYMENT_SUCCESS",
    "processedAt": "2025-03-19T10:30:00Z"
}
```

Status values:
- `"PAYMENT_SUCCESS"` — payment approved (current simulation always returns this)
- `"PAYMENT_FAILURE"` — payment declined (add to simulate failure scenarios)

---

## KafkaConsumerConfig and KafkaProducerConfig

`payment-service` has explicit Kafka config classes:

`org.cedro.paymentservice.config.KafkaConsumerConfig`:
```java
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-group");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
```

`org.cedro.paymentservice.config.KafkaProducerConfig`:
```java
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

---

## Extending with Real Payment Gateway

To replace `simulatePayment()` with a real payment gateway (e.g., Stripe, PagSeguro):

```java
@Service
public class PaymentService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StripePaymentGateway stripeGateway;  // Feign client or SDK

    public PaymentService(KafkaTemplate<String, String> kafkaTemplate,
                          StripePaymentGateway stripeGateway) {
        this.kafkaTemplate = kafkaTemplate;
        this.stripeGateway = stripeGateway;
    }

    @KafkaListener(topics = "vendas-topico", groupId = "payment-group")
    public void processarVenda(String orderId) {
        log.info("Processing payment for order: {}", orderId);
        try {
            StripeChargeResult result = stripeGateway.charge(orderId);
            String status = result.isSucceeded() ? "PAYMENT_SUCCESS" : "PAYMENT_FAILURE";
            publishResult(orderId, status);
        } catch (StripeException e) {
            log.error("Stripe API error for order {}: {}", orderId, e.getMessage());
            publishResult(orderId, "PAYMENT_FAILURE");
        }
    }

    private void publishResult(String orderId, String status) {
        String payload = "{\"orderId\":\"%s\",\"status\":\"%s\",\"processedAt\":\"%s\"}"
            .formatted(orderId, status, Instant.now());
        kafkaTemplate.send(PAYMENT_PROCESSED_TOPIC, orderId, payload);
        log.info("Payment result published: orderId={}, status={}", orderId, status);
    }
}
```

For payment data that needs to be persisted (transaction IDs, charge references), add a MongoDB
collection to `payment-service` and a corresponding entity in `order-model`.

---

## Why payment-service Has No REST Endpoints

Payment is a background process triggered by events. There is no external API that clients
need to call on `payment-service`. All interactions happen via Kafka.

If a client needs to know the payment status of an order, they query `order-service`:
```
GET /order-service/orderflow/v1/order/{orderId}
→ Returns Order with status=COMPLETED/CANCELLED and approvalStatus=APPROVED/REJECTED
```
