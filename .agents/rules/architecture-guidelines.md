# Architecture Guidelines — OrderFlow

These rules define how the system is structured and how services interact. Violating these
guidelines creates coupling, security gaps, and maintenance problems.

---

## Service Responsibilities

Each service has a single, well-defined responsibility. Never merge responsibilities:

| Service                | Responsibility                                              |
|------------------------|-------------------------------------------------------------|
| `order-service`        | Order lifecycle CRUD + Kafka producer for `vendas-topico`   |
| `payment-service`      | Payment simulation — consumes `vendas-topico`, publishes `payment-processed` |
| `notification-service` | Customer notification — consumes `payment-processed`        |
| `inventory-service`    | Product catalog CRUD                                        |
| `order-security-server`| JWT issuance (login, JWKS endpoint)                         |
| `gateway-server`       | Single entry point, JWT validation, service discovery routing|
| `eureka-server`        | Service registry                                            |
| `config-server`        | Centralized external configuration (optional)               |

---

## Shared Library Rules

### `order-model`
- All domain entities: `Order`, `Product`, `AppUser`, `OrderItem`, `Metadata`, `Payment`,
  `Notification`, `Role`, `OrderStatus`, `ApprovalStatus`.
- No business logic. No Spring beans. Pure POJOs with Lombok and Jakarta Validation.
- Any service that needs a domain entity adds `order-model` as a dependency — never redefines it.

### `order-rest-service`
- `AbstractController<T>` — base controller delegating to `AbstractService<T>`.
- `AbstractService<T>` — base service with CRUD + dynamic query via `MongoTemplate`.
- `Rest<T>` interface — defines the REST contract (save, update, deleteById, findById, list, count).
- Nothing else belongs here.

### `order-utils`
- `MicroserviceSecurityConfig` — autoconfigured `SecurityFilterChain` for all microservices.
- `ResourceExceptionHandler` — `@ControllerAdvice` for `ResourceNotFoundException`,
  `AccessDeniedException`, `AuthenticationException`.
- `ResourceNotFoundException` — the only exception services should throw for 404 scenarios.
- `ErrorView` — uniform error response body.
- `SecurityContextHelper` — utility to extract JWT claims from `SecurityContextHolder`.
- `ViaCep` (Feign client) + `Endereco` record — external address lookup.
- No business logic from individual services belongs here.

---

## REST Layer: Every Service MUST Extend AbstractController + AbstractService

The template method pattern provides CRUD for free. Use it:

```
ProductController extends AbstractController<Product>
    → ProductServiceImpl extends AbstractService<Product>
        → ProductRepository extends MongoRepository<Product, String>
        → MongoTemplate (injected by AbstractService)
```

Any custom business method goes into the service interface + impl:

```java
// In ProductService interface:
List<Product> findLowStock(int threshold);

// In ProductServiceImpl:
@Override
public List<Product> findLowStock(int threshold) {
    Query query = new Query(
        Criteria.where("stockQuantity").lt(threshold)
                .and("metadata.deleted").ne(true)
    );
    return mongoTemplate.find(query, Product.class);
}
```

---

## Entity Location

New entities ALWAYS go in `order-model`. The workflow:

1. Define entity in `order-model/src/main/java/org/cedro/ordermodel/model/`.
2. Run `mvn clean install` on `order-model` to update the local JAR.
3. Add the entity to the service that owns it via `order-model` dependency (already present in POMs).

Never create entity classes inside service modules.

---

## Service Communication

### Async (Kafka) — Default for inter-service events
```
order-service ──[vendas-topico]──► payment-service ──[payment-processed]──► notification-service
                                                    │
                                                    └──[payment-processed]──► order-service (status update)
```

Rules:
- Use Kafka for all fire-and-forget and event-driven communication between services.
- The producer does not know about consumers. Services are fully decoupled.
- Never call `order-service` from `payment-service` via HTTP to update the order status — use Kafka.

### Sync (Feign) — Only for external APIs
- `ViaCep` in `order-utils` is the only sanctioned Feign client.
- To add a new external client, create it in `order-utils`.
- Never use `RestTemplate`. Never use `WebClient` for synchronous service-to-service calls.

### Gateway routing
- All external HTTP traffic enters through `gateway-server:8080`.
- The gateway discovers services via Eureka (`lower-case-service-id: true`).
- Route pattern: `http://localhost:8080/{service-name}/{path}` → routed to the actual service.
- Example: `GET http://localhost:8080/order-service/orderflow/v1/order` → `order-service:8081`.

---

## Gateway Architecture (WebFlux)

The gateway uses Spring WebFlux (reactive). This has critical implications:

- Security config uses `@EnableWebFluxSecurity` and `ServerHttpSecurity` (NOT `HttpSecurity`).
- `SecurityWebFilterChain` is the return type (NOT `SecurityFilterChain`).
- `ReactiveJwtAuthenticationConverter` is used at the gateway (NOT `JwtAuthenticationConverter`).
- Never import `org.springframework.security.config.annotation.web.builders.HttpSecurity` in gateway.
- Never add `spring-boot-starter-web` to gateway — it uses `spring-boot-starter-webflux`.

---

## JWT: Defense in Depth

JWT is validated at TWO layers:

1. **Gateway layer** (`gateway-server`): validates JWT signature against JWKS endpoint
   (`http://localhost:9999/.well-known/jwks.json`). Rejects invalid tokens before routing.
2. **Service layer** (`MicroserviceSecurityConfig`): validates JWT again at each microservice.
   Provides protection against direct port access and internal network calls.

This is intentional. Both layers must remain active.

---

## Eureka Registration

Every service MUST register with Eureka:

```yaml
eureka:
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

Exception: `eureka-server` itself sets both to `false`.

---

## Config Server

Config server is optional. Services use:

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

The `optional:` prefix means the service starts normally if config server is down.
Never make config server a hard dependency.

---

## Soft Delete Mandate

`deleteById` on MongoDB MUST never permanently delete records in production logic.

The correct pattern (soft delete):
```java
@Override
public void deleteById(String id) {
    orderRepository.findById(id).ifPresentOrElse(order -> {
        order.getMetadata().setDeleted(true);
        order.getMetadata().setDeletedAt(LocalDateTime.now());
        order.getMetadata().setDeletedBy(SecurityContextHelper.getCurrentUsername());
        orderRepository.save(order);
        log.info("Order {} soft-deleted", id);
    }, () -> {
        throw new ResourceNotFoundException(new ErrorView(
            LocalDateTime.now(), "Order not found", HttpStatus.NOT_FOUND, "Not Found", id, Order.class
        ));
    });
}
```

The base `AbstractService.buildQuery()` already excludes soft-deleted records from `list()` and
`count()` by filtering `metadata.deleted != true`.

---

## Kafka Consumer Isolation

Each consumer group has a distinct group ID:

| Consumer                  | Group ID              | Topic consumed      |
|---------------------------|-----------------------|---------------------|
| `payment-service`         | `payment-group`       | `vendas-topico`     |
| `order-service` (status)  | `order-group`         | `payment-processed` |
| `notification-service`    | `notification-group`  | `payment-processed` |

Multiple consumers on `payment-processed` (order-service and notification-service) work correctly
because each has its own group ID — Kafka delivers the message to each group independently.

---

## Adding a New Microservice Checklist

1. Create Maven module under root `pom.xml`.
2. Add dependencies: `order-model`, `order-rest-service`, `order-utils`, `spring-boot-starter-web`,
   `spring-boot-starter-data-mongodb`, `spring-cloud-starter-netflix-eureka-client`.
3. Configure `application.yml` with: `spring.application.name`, MongoDB URI, Eureka registration,
   JWT JWKS URI, server port.
4. Do NOT define a `SecurityFilterChain` bean — `order-utils` autoconfigures it.
5. Create entity in `order-model` if needed.
6. Create repository extending `MongoRepository<Entity, String>`.
7. Create service interface + impl extending `AbstractService<Entity>`.
8. Create controller extending `AbstractController<Entity>` with all `@PreAuthorize` overrides.
9. Register service in Eureka config.
10. Add gateway route or rely on discovery locator (`lower-case-service-id: true`).
