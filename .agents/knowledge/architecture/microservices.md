# Microservices Architecture — OrderFlow

OrderFlow is a Java 17 / Spring Boot 3.4.3 multi-module Maven project composed of eight
independently deployable services backed by two shared libraries and one domain model module.

---

## Single Responsibility Per Service

Each service owns one domain slice:

| Service                | Domain                         | Persistence         |
|------------------------|--------------------------------|---------------------|
| `order-service`        | Order lifecycle management     | MongoDB `orders`    |
| `payment-service`      | Payment processing             | None (stateless)    |
| `notification-service` | Customer notification          | None (stateless)    |
| `inventory-service`    | Product catalog management     | MongoDB `products`  |
| `order-security-server`| Identity / JWT issuance        | MongoDB `users`     |

Infrastructure services:
- `gateway-server`: routing, JWT validation, entry point
- `eureka-server`: service registry
- `config-server`: externalized configuration

Shared libraries (not deployable as services):
- `order-model`: domain entities
- `order-rest-service`: abstract REST layer (AbstractController, AbstractService)
- `order-utils`: cross-cutting concerns (security, exception handling, Feign)

---

## Shared Library Pattern

Instead of duplicating security configuration and exception handling in every service,
common concerns are extracted into shared libraries:

```
order-model (entities + enums)
    ├── used by order-service
    ├── used by payment-service (if it references Order)
    ├── used by inventory-service
    └── used by order-security-server (AppUser)

order-rest-service (AbstractController, AbstractService, Rest interface)
    ├── used by order-service
    └── used by inventory-service

order-utils (MicroserviceSecurityConfig, ResourceExceptionHandler, ViaCep)
    ├── used by order-service
    └── used by inventory-service
```

This eliminates code duplication and enforces consistent behavior across services.
Any change to `ResourceExceptionHandler` is instantly available to all consuming services.

---

## Template Method Pattern: AbstractController + AbstractService

The Template Method pattern provides a standard CRUD implementation that services customize
by overriding specific steps.

```
AbstractController<T>          AbstractService<T>
    ├── save()         →           ├── save()         (override to add Kafka publish)
    ├── update()       →           ├── update()
    ├── deleteById()   →           ├── deleteById()   (override to add soft delete)
    ├── findById()     →           ├── findById()
    ├── list()         →           ├── list()         (uses MongoTemplate + dynamic query)
    └── count()        →           └── count()
```

`AbstractService` provides:
- `MongoTemplate` injection (via `@Autowired`) for dynamic queries.
- `buildQuery()` that auto-applies `metadata.deleted != true` filter and maps request params.
- Default implementations for all CRUD operations.

Services override only what they need to customize.

---

## Service Boundaries and Data Ownership

Each service owns its data. No service accesses another service's database directly:

```
order-service    ──owns──▶ MongoDB orders collection
payment-service  ──stateless (no own collection currently)
notification-service ──stateless (no own collection currently)
inventory-service ──owns──▶ MongoDB products collection
order-security-server ──owns──▶ MongoDB users collection
```

Cross-boundary data access must go through the service's API (Kafka events or HTTP via gateway).
Direct MongoDB cross-collection queries from a different service are forbidden.

---

## Maven Multi-Module Structure

Root `pom.xml` declares all modules:

```xml
<modules>
    <module>order-model</module>
    <module>order-rest-service</module>
    <module>order-utils</module>
    <module>order-security-server</module>
    <module>gateway-server</module>
    <module>eureka-server</module>
    <module>config-server</module>
    <module>order-service</module>
    <module>payment-service</module>
    <module>notification-service</module>
    <module>inventory-service</module>
</modules>
```

Build order is determined by Maven dependency resolution. Shared libraries build first.

Build command: `mvn clean install -DskipTests` (from root directory)

---

## Dependency Direction (No Circular Dependencies)

```
Services depend on:
    order-model (entities)
    order-rest-service (abstract layer)
    order-utils (security, exceptions)

order-rest-service depends on:
    order-model (uses T extends domain entities)
    spring-data-mongodb (MongoTemplate in AbstractService)

order-utils depends on:
    order-model (AppUser referenced in SecurityContextHelper)
    spring-security (MicroserviceSecurityConfig)

order-model depends on:
    nothing (pure domain, only Lombok + Jakarta Validation)
```

No circular dependencies. Libraries do not depend on services.

---

## Inter-Service Communication Patterns

```
REST (external, via gateway):
    Client → gateway:8080 → order-service:8081

Kafka (async, inter-service):
    order-service → [vendas-topico] → payment-service
    payment-service → [payment-processed] → order-service
    payment-service → [payment-processed] → notification-service

Feign (sync, external only):
    order-service → ViaCep (https://viacep.com.br) for address lookup
```

Services never call each other via HTTP directly.
Services never bypass the gateway by calling each other's ports.

---

## Why payment-service and notification-service Don't Have REST Endpoints

`payment-service` and `notification-service` are event-driven consumers. They:
- Have no data owned beyond what's in Kafka.
- Have no state that external clients need to query.
- Do their work entirely through Kafka event processing.

Adding REST endpoints to these services would break single responsibility and create coupling.
If payment status needs to be queried, it is stored in the `order-service` via Kafka update.

---

## Observability

Current state (MVP):
- All services expose `/actuator/health` (Spring Boot Actuator).
- SLF4J logging to stdout (structured logging not yet implemented).
- Kafka events visible in Kafdrop at `http://localhost:9000`.

Future work:
- Distributed tracing (Micrometer + Zipkin/Jaeger).
- Structured JSON logging for log aggregation.
- Metrics export to Prometheus + Grafana.
