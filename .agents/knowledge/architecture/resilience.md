# Resilience Patterns — OrderFlow

Current resilience posture, what's missing, and future integration points.

---

## Current Resilience Mechanisms

### Kafka Consumer Offset Retry

The most significant resilience feature currently in place is Kafka's consumer offset management.

When `payment-service` or `notification-service` goes down:
1. Kafka retains messages in the topic (default retention: 7 days).
2. Consumer last committed offset is saved in Kafka.
3. When the consumer restarts, it reads from where it left off (`auto-offset-reset: earliest` for
   new groups, last committed offset for existing groups).

This provides durable, at-least-once delivery without any application-level retry code.

### Soft Delete as Data Resilience

Soft delete (`metadata.deleted = true`) prevents accidental data loss. All "deleted" records
remain in MongoDB and can be recovered by setting `deleted = false`.

### MongoDB Connection Resilience

Spring Data MongoDB uses connection pooling with automatic reconnection. If MongoDB is
temporarily unavailable, pending operations fail with exceptions. Retries must be added manually
or via Resilience4j (future work).

### optional: Config Server Import

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

Services start normally without config server — they use local `application.yml` defaults.

---

## What Is Missing (Current MVP Gaps)

### Circuit Breaker

No Circuit Breaker is implemented. If `ViaCep` (Feign client) is down, every call hangs until
Feign's default timeout (10 seconds). If timeout is not configured, it may hang indefinitely.

Impact:
- A slow/down ViaCEP API blocks the `order-service` thread pool.
- Could cascade to all order creation requests being slow.

Future fix with Resilience4j:
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

```java
@FeignClient(name = "viacep", url = "https://viacep.com.br/ws",
             fallback = ViaCepFallback.class)
public interface ViaCep {
    @GetMapping("/{cep}/json/")
    Endereco findByCep(@PathVariable("cep") String cep);
}

@Component
public class ViaCepFallback implements ViaCep {
    @Override
    public Endereco findByCep(String cep) {
        return new Endereco(cep, "Unknown", "Unknown", "Unknown", "Unknown");
    }
}
```

### Retry for Transient Failures

No retry logic on producer sends or service operations. If MongoDB is briefly unavailable,
a save operation fails immediately.

Future fix:
```java
@Retryable(retryFor = {MongoException.class}, maxAttempts = 3,
           backoff = @Backoff(delay = 500, multiplier = 2))
public Order saveWithRetry(Order order) {
    return orderRepository.save(order);
}
```

Kafka producer retries are configured via:
```yaml
spring:
  kafka:
    producer:
      retries: 3
      properties:
        retry.backoff.ms: 1000
```

### Dead-Letter Queue (DLQ)

Currently, unprocessable Kafka messages are logged and discarded. A DLQ would preserve failed
messages for investigation and manual reprocessing.

Future fix:
```java
@Bean
public DeadLetterPublishingRecoverer recoverer(KafkaTemplate<Object, Object> template) {
    return new DeadLetterPublishingRecoverer(template,
        (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
}

@Bean
public DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer recoverer) {
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
}
```

This would create `vendas-topico.DLT` and `payment-processed.DLT` topics for failed messages.

### Bulkhead

No thread pool isolation. All services use the default Spring MVC thread pool.
High traffic on one endpoint can exhaust threads needed by other endpoints.

Future fix: Resilience4j `@Bulkhead` on critical methods.

### Rate Limiting at Gateway

No rate limiting. A client could flood any service with requests.

Future fix via Spring Cloud Gateway:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
```

---

## Resilience Priorities for Production

Based on current gaps, priority order for implementation:

1. **Kafka producer retries** — easiest, highest impact, just YAML config.
2. **Feign timeout + Circuit Breaker on ViaCEP** — prevents thread pool exhaustion.
3. **DLQ for Kafka consumers** — prevents silent message loss.
4. **Rate limiting at gateway** — security and stability.
5. **Retry with backoff for MongoDB operations** — requires Resilience4j.
6. **Bulkhead** — advanced, needed at scale.

---

## Service Health Endpoints

All services expose `/actuator/health`. For production readiness, configure:

```yaml
management:
  endpoint:
    health:
      show-details: always
  health:
    mongo:
      enabled: true
    kafka:
      enabled: true
```

This reports MongoDB and Kafka connectivity status in the health check response.
