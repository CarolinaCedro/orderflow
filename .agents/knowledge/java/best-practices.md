# Java 17 Best Practices — OrderFlow

Java 17 LTS features actively used or recommended for this project.

---

## Records for DTOs and Value Objects

Use Java records for immutable data transfer objects. They are ideal for request/response shapes
that do not need to be entities.

```java
// In order-security-server — login request DTO
public record LoginRequest(String username, String password) {}

// In order-security-server — token response DTO
public record TokenResponse(String accessToken, Long expiresIn) {}

// For ViaCEP response in order-utils
public record Endereco(
    String cep,
    String logradouro,
    String bairro,
    String localidade,
    String uf
) {}
```

Rules:
- Use records for DTOs that cross service boundaries (request/response bodies).
- Do NOT use records as MongoDB `@Document` entities — MongoDB needs setters and the record's
  immutability conflicts with Spring Data's hydration pattern.
- Records work perfectly as nested value objects when stored inside a document.

---

## Sealed Classes for Closed Hierarchies

Use sealed classes for modeling a closed set of subtypes — e.g., payment result:

```java
public sealed interface PaymentResult permits PaymentSuccess, PaymentFailure {}

public record PaymentSuccess(String orderId, String transactionId) implements PaymentResult {}

public record PaymentFailure(String orderId, String reason) implements PaymentResult {}
```

With pattern matching in switch:
```java
String formatResult(PaymentResult result) {
    return switch (result) {
        case PaymentSuccess s -> "Order %s paid (tx: %s)".formatted(s.orderId(), s.transactionId());
        case PaymentFailure f -> "Order %s failed: %s".formatted(f.orderId(), f.reason());
    };
}
```

This is preferred over `instanceof` chains and `if-else` for polymorphic dispatch.

---

## Text Blocks for Multi-line Strings

Use text blocks for JSON templates in tests and Kafka payload construction:

```java
// Instead of string concatenation:
String payload = """
    {
        "orderId": "%s",
        "status": "%s",
        "processedAt": "%s"
    }
    """.formatted(orderId, status, Instant.now());
```

Text blocks eliminate escaping hell and improve readability.

---

## `var` for Local Type Inference

Use `var` when the type is obvious from the right-hand side:

```java
// Clear — type is obvious
var savedOrder = orderRepository.save(order);
var orderId = savedOrder.getId();
var query = new Query(Criteria.where("metadata.deleted").ne(true));

// Avoid — type is not obvious, reduces readability
var result = someService.processComplexOperation(params);
```

Rules:
- Use `var` for local variables where the right-hand side makes the type clear.
- Never use `var` for method parameters or return types.
- Never use `var` when the type would be ambiguous to a reader.

---

## Stream API

Use streams for transforming collections. Key patterns in this project:

```java
// Extract role names for JWT claims (TokenService)
List<String> roles = user.getRoles().stream()
    .map(Enum::name)
    .toList();  // Java 16+ — unmodifiable list, preferred over .collect(Collectors.toList())

// Filter request params, excluding reserved keys (AbstractService)
params.entrySet().stream()
    .filter(e -> !RESERVED_PARAMS.contains(e.getKey()))
    .forEach(e -> query.addCriteria(Criteria.where(e.getKey()).is(e.getValue())));

// Aggregate order total from items
BigDecimal total = order.getItems().stream()
    .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

Avoid:
- Streams with side effects (except `forEach` for logging or building external state).
- Deeply nested stream pipelines — extract to named intermediate variables.
- `collect(Collectors.toList())` — use `.toList()` (Java 16+) for unmodifiable lists.
- `collect(Collectors.toUnmodifiableList())` — verbose, `.toList()` is equivalent.

---

## Optional Usage

Use `Optional` for values that may legitimately be absent:

```java
// Correct — from repository lookup
orderRepository.findById(id)
    .map(ResponseEntity::ok)
    .orElseThrow(() -> new ResourceNotFoundException(errorView));

// Pattern from AuthController
appUserRepository.findByUsername(request.username())
    .filter(user -> user.isActive() && passwordEncoder.matches(request.password(), user.getPassword()))
    .map(user -> ResponseEntity.ok(new TokenResponse(tokenService.generateToken(user), 3600L)))
    .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

// Correct use of ifPresentOrElse (OrderPaymentListener)
orderRepository.findById(orderId).ifPresentOrElse(
    order -> { /* update order */ },
    () -> log.error("Order {} not found for payment update", orderId)
);
```

Rules:
- NEVER call `.get()` on an `Optional` without `.isPresent()` check — use `.orElse()` or
  `.orElseThrow()`.
- NEVER return `null` from a method that could return `Optional<T>`.
- Do NOT use `Optional` as a method parameter — use method overloading instead.
- Do NOT use `Optional` for collection fields — use empty list instead.

---

## Immutability Patterns

Prefer immutable structures where possible:

```java
// Immutable set of reserved params (AbstractService)
private static final Set<String> RESERVED_PARAMS = Set.of("page", "size", "sort", "returnEntity");

// Immutable list from stream
List<String> roles = user.getRoles().stream().map(Enum::name).toList();

// Immutable map
Map<String, String> config = Map.of("key1", "value1", "key2", "value2");
```

For domain entities, immutability conflicts with Spring Data MongoDB's setter requirements.
Use Lombok `@Data` (generates setters) on entities, but restrict mutation to the service layer.

---

## Collection Factory Methods

Use Java 9+ factory methods for fixed collections:

```java
Set.of("ADMIN", "MANAGER")              // Immutable set
List.of("vendas-topico", "payment-processed")  // Immutable list
Map.of("status", "PAYMENT_SUCCESS")     // Immutable map (up to 10 entries)
Map.ofEntries(                           // More than 10 entries
    Map.entry("key1", "value1"),
    Map.entry("key2", "value2")
)
```

These throw `NullPointerException` on null elements — use only when values are guaranteed non-null.

---

## String Formatting

Prefer `String.formatted(...)` over `String.format(...)`:

```java
// Modern (Java 15+)
String payload = "{\"orderId\":\"%s\",\"status\":\"%s\"}".formatted(orderId, status);

// Or use text blocks with .formatted()
String payload = """
    {"orderId":"%s","status":"%s","processedAt":"%s"}
    """.strip().formatted(orderId, status, Instant.now());
```

---

## Pattern Matching for instanceof

```java
// Old style
if (result instanceof PaymentSuccess) {
    PaymentSuccess success = (PaymentSuccess) result;
    log.info("Success: {}", success.transactionId());
}

// Modern (Java 16+)
if (result instanceof PaymentSuccess success) {
    log.info("Success: {}", success.transactionId());
}
```

Use pattern matching `instanceof` to eliminate redundant casts.
