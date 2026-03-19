# Java Performance Best Practices — OrderFlow

Performance considerations specific to the OrderFlow stack: MongoDB, Kafka, Spring WebFlux gateway,
and multi-service communication.

---

## MongoDB: Avoiding N+1 Queries

The N+1 problem in MongoDB occurs when you iterate a collection and make a separate DB call
for each item.

### Anti-pattern: N+1 via loop
```java
// WRONG — executes one findById per order item
public List<Product> getProductsForOrder(Order order) {
    return order.getItems().stream()
        .map(item -> productRepository.findById(item.getProductId()).orElseThrow())
        .toList();
}
```

### Correct: batch lookup with $in
```java
// CORRECT — one query for all products
public List<Product> getProductsForOrder(Order order) {
    List<String> productIds = order.getItems().stream()
        .map(OrderItem::getProductId)
        .toList();

    Query query = new Query(
        Criteria.where("_id").in(productIds)
                .and("metadata.deleted").ne(true)
    );
    return mongoTemplate.find(query, Product.class);
}
```

---

## MongoDB: Use Projections

When you only need a subset of fields, use projections to reduce network transfer and
deserialization cost:

```java
// Full document fetch — avoid when only status is needed
Order order = orderRepository.findById(orderId).orElseThrow();

// Projection — fetch only status field
Query query = new Query(Criteria.where("_id").is(orderId));
query.fields().include("status").include("approvalStatus");
Order projected = mongoTemplate.findOne(query, Order.class);
```

For read-heavy endpoints (list, count), avoid fetching embedded documents you don't need.

---

## MongoDB: Indexing Recommendations

Queries that filter by `metadata.deleted`, `status`, `customerId`, or `category` should
have indexes. Without indexes, MongoDB performs a full collection scan.

Recommended indexes for `orders` collection:
```java
@Document(collection = "orders")
@CompoundIndex(name = "customer_status_idx", def = "{'customerId': 1, 'status': 1}")
@CompoundIndex(name = "deleted_status_idx", def = "{'metadata.deleted': 1, 'status': 1}")
public class Order { ... }
```

Recommended indexes for `products`:
```java
@Document(collection = "products")
@CompoundIndex(name = "category_active_idx", def = "{'category': 1, 'isActive': 1}")
@CompoundIndex(name = "deleted_category_idx", def = "{'metadata.deleted': 1, 'category': 1}")
public class Product { ... }
```

The `metadata.deleted` field appears in every query (AbstractService.buildQuery) — it must be
indexed in every collection to avoid full scans.

---

## MongoDB: Connection Pooling

MongoDB connection pooling is managed by the driver. Default pool size is 100 connections.
For production, tune via the URI or `MongoClientSettings`:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://root:products@localhost:27018/orderflow?authSource=admin&maxPoolSize=50&minPoolSize=5
```

Or via `@Configuration`:
```java
@Bean
public MongoClientSettings mongoClientSettings() {
    return MongoClientSettings.builder()
        .applyConnectionString(new ConnectionString(mongoUri))
        .applyToConnectionPoolSettings(builder ->
            builder.maxSize(50).minSize(5).maxWaitTime(5, TimeUnit.SECONDS)
        )
        .build();
}
```

---

## Kafka: Batch Processing

For high-throughput scenarios, enable batch consumption instead of processing one message at a time:

```java
// Single message processing (current pattern — fine for low volume)
@KafkaListener(topics = "vendas-topico", groupId = "payment-group")
public void processarVenda(String orderId) { ... }

// Batch processing — higher throughput
@KafkaListener(topics = "vendas-topico", groupId = "payment-group",
               containerFactory = "batchContainerFactory")
public void processarVendas(List<String> orderIds) {
    log.info("Processing batch of {} orders", orderIds.size());
    orderIds.forEach(orderId -> {
        // process each
    });
}
```

Batch container factory configuration:
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> batchContainerFactory(
        ConsumerFactory<String, String> consumerFactory) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(true);
    factory.getContainerProperties().setPollTimeout(3000);
    return factory;
}
```

Use batch processing when message rate exceeds ~100/second. The current simulation volume
does not require it.

---

## Kafka: Producer Performance

`KafkaTemplate.send()` is asynchronous by default — it does not block the calling thread.

```java
// send() returns a CompletableFuture — don't block on it in hot paths
kafkaTemplate.send(VENDAS_TOPICO, orderId, orderId);
// The above is fire-and-forget — the order is saved before confirmation arrives

// If you need confirmation (adds latency):
CompletableFuture<SendResult<String, String>> future =
    kafkaTemplate.send(VENDAS_TOPICO, orderId, orderId);
future.whenComplete((result, ex) -> {
    if (ex == null) {
        log.info("Order {} delivered to partition {}", orderId,
                 result.getRecordMetadata().partition());
    } else {
        log.error("Failed to deliver order {}", orderId, ex);
    }
});
```

For OrderFlow's current volume, fire-and-forget is acceptable.

---

## Gateway: Avoid Blocking in Reactive Pipeline

The gateway uses Spring WebFlux (reactive, non-blocking). Blocking calls on the reactive event loop
thread cause thread starvation.

```java
// FORBIDDEN in gateway code — blocks the Netty IO thread
String result = someBlockingRepository.findById(id); // Never do this in WebFlux

// Correct — wrap blocking work in a bounded Scheduler
Mono<String> result = Mono.fromCallable(() -> someBlockingRepository.findById(id))
    .subscribeOn(Schedulers.boundedElastic());
```

The current `GatewaySecurityConfig` is fully non-blocking — do not add blocking logic to it.

Filters added to the gateway must be reactive:
```java
@Component
public class LoggingFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("Request: {} {}", exchange.getRequest().getMethod(),
                  exchange.getRequest().getURI());
        return chain.filter(exchange); // Always return the Mono chain
    }
}
```

---

## Lazy Initialization

For services that have heavy initialization (e.g., RSA key generation), Spring Boot's lazy
initialization reduces startup time:

```yaml
spring:
  main:
    lazy-initialization: true
```

However, lazy initialization delays error detection to first request. Use selectively with
`@Lazy` on specific beans rather than globally.

The `KeyConfig.rsaKey()` bean runs at startup — this is intentional and fast. Do not make it lazy.

---

## Startup Time Optimization

Service startup order matters — services must wait for dependencies:

1. `eureka-server` — start first
2. `config-server` — start second (optional for others)
3. `order-security-server` — start before services need JWT validation
4. `order-service`, `payment-service`, `inventory-service`, `notification-service` — any order
5. `gateway-server` — start last (needs Eureka populated)

With Docker Compose, use `depends_on` + `healthcheck` to enforce startup order.

---

## ObjectMapper Reuse

`ObjectMapper` is thread-safe and expensive to construct. Always reuse a single instance:

```java
// CORRECT — field-level singleton (as in PaymentService, NotificationService, OrderPaymentListener)
private final ObjectMapper objectMapper = new ObjectMapper();

// Or inject the Spring-managed instance
@Autowired
private ObjectMapper objectMapper;

// WRONG — new instance per call
public void process(String payload) {
    JsonNode node = new ObjectMapper().readTree(payload); // Expensive, wasteful
}
```
