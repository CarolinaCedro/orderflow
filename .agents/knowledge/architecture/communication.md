# Service Communication — OrderFlow

How services communicate, when to use each pattern, and what is explicitly forbidden.

---

## Communication Patterns in Use

### 1. Kafka (Async, Event-Driven)

Used for: all inter-service communication triggered by business events.

```
order-service ──[vendas-topico]──────────────► payment-service
                                                        │
                                                        └──[payment-processed]──► order-service (status update)
                                                        └──[payment-processed]──► notification-service
```

Characteristics:
- Fire-and-forget: `order-service` does not wait for payment to complete.
- Decoupled: `order-service` does not know `payment-service` exists.
- Durable: messages persist in Kafka until consumed (configurable retention).
- Retry: if a consumer is down, it processes messages when it comes back up (from its last offset).

### 2. OpenFeign (Sync, External APIs Only)

Used for: external HTTP APIs that do not have a Kafka equivalent.

```
order-service ──[HTTP GET]──► https://viacep.com.br/ws/{cep}/json/
```

Current external Feign clients:
- `ViaCep` in `order-utils` — address lookup by postal code (CEP).

Characteristics:
- Synchronous — the calling thread blocks until the response arrives.
- Circuit breaker should protect this call (not yet implemented).
- Timeout configured via Feign or RestClient settings.

### 3. HTTP via Gateway (External Clients)

Used for: all requests from external clients (frontend, mobile, API consumers).

```
External Client ──[HTTP + JWT]──► gateway-server:8080 ──[routed]──► service:port
```

The gateway is the only entry point for external traffic. Services do not expose ports
externally in production.

---

## When to Use Kafka vs Feign

| Scenario                                        | Use Kafka | Use Feign |
|-------------------------------------------------|-----------|-----------|
| Order created → trigger payment                 | YES       | NO        |
| Payment done → trigger notification             | YES       | NO        |
| Address lookup for order delivery               | NO        | YES (ViaCep) |
| Inventory check before order approval           | Kafka preferred (async) | Feign if synchronous check is required |
| Order service querying payment status           | NO — use Kafka (order-service subscribes to payment-processed) | NO |
| New service needing data from order-service     | YES (subscribe to relevant topic) | NO (never call order-service HTTP directly) |

Decision rule: if both services are internal to OrderFlow, use Kafka. If it's an external
third-party API, use Feign.

---

## What Is Explicitly Forbidden

### RestTemplate

`RestTemplate` is deprecated in Spring 5+ and removed from idiomatic Spring Boot 3 code.
Never use it:

```java
// FORBIDDEN
RestTemplate restTemplate = new RestTemplate();
String result = restTemplate.getForObject("http://order-service/orderflow/v1/order/" + id, String.class);
```

Use Feign for sync external calls. Use Kafka for async inter-service communication.

### Direct HTTP Between Services (Bypassing Gateway)

```java
// FORBIDDEN — directly calling another service port
WebClient.create("http://localhost:8085/payment-service/pay")
    .post()
    .retrieve()
    .bodyToMono(String.class);
```

All inter-service HTTP traffic in production must go through the gateway.

### Direct Database Cross-Access

```java
// FORBIDDEN — payment-service reading order-service's MongoDB collection
@Autowired
private OrderRepository orderRepository;  // payment-service cannot have this
```

Each service owns its database. Cross-collection queries are forbidden.

---

## Gateway Routing via Eureka

The gateway discovers services dynamically from Eureka. No hardcoded routes needed:

```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
```

Route resolution:
1. Request arrives at `gateway:8080`.
2. Gateway reads the first path segment as the service ID.
3. Looks up `SERVICE-ID` in Eureka (converted to uppercase for lookup).
4. Forwards request to the service's registered host:port.
5. Strips the service ID prefix from the path.

Example: `GET /order-service/orderflow/v1/order`
→ Eureka lookup: `ORDER-SERVICE`
→ Found at: `localhost:8081`
→ Forwarded as: `GET http://localhost:8081/orderflow/v1/order`

---

## Kafka Topic Design

### vendas-topico

- **Purpose**: Order created event.
- **Key**: `orderId` (ensures ordering for the same order).
- **Value**: `orderId` (plain string — the payment service fetches order details from context).
- **Producers**: `order-service`.
- **Consumers**: `payment-service` (group: `payment-group`).

Design rationale: The message is intentionally minimal (just the orderId). The payment service
does not need the full order data to simulate payment. If real payment logic needs order details,
either include them in the message payload (denormalized) or query `order-service` via its API.

### payment-processed

- **Purpose**: Payment result event.
- **Key**: `orderId`.
- **Value**: JSON string:
  ```json
  {"orderId":"abc123","status":"PAYMENT_SUCCESS","processedAt":"2025-03-19T10:30:00Z"}
  ```
- **Producers**: `payment-service`.
- **Consumers**: `order-service` (group: `order-group`), `notification-service` (group: `notification-group`).

Design rationale: Multiple consumers with independent group IDs. Each consumer processes the event
independently. This is the fan-out pattern — one event, multiple independent handlers.

---

## Adding a New Consumer Without Touching Producers

This is a key advantage of Kafka's publish-subscribe model. To add a new service that reacts to
order payments:

1. Create a new service (e.g., `erp-service`).
2. Configure `bootstrap-servers: localhost:9091`.
3. Add a `@KafkaListener` on `payment-processed` with a unique group ID (e.g., `erp-group`).
4. Process the event without modifying `payment-service` at all.

```java
@KafkaListener(topics = "payment-processed", groupId = "erp-group")
public void onPaymentProcessed(String payload) {
    // Parse and forward to ERP system
}
```

The producer (`payment-service`) remains unchanged. This is true decoupling.
