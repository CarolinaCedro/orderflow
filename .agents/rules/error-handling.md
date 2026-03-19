# Error Handling Rules — OrderFlow

All error handling follows a centralized, uniform pattern. These rules are non-negotiable.

---

## The Error Stack

```
HTTP Request
    │
    ▼
Controller method
    │ throws ResourceNotFoundException (404)
    │ throws AccessDeniedException (403) — Spring Security
    │ throws AuthenticationException (401) — Spring Security
    │ throws MethodArgumentNotValidException (400) — Spring validation
    │
    ▼
ResourceExceptionHandler (@ControllerAdvice in order-utils)
    │
    ▼
ErrorView → HTTP Response
```

---

## ResourceExceptionHandler (order-utils)

Located at: `org.cedro.orderutils.infra.handler.ResourceExceptionHandler`

Handles:
- `ResourceNotFoundException` → `404 Not Found`
- `AccessDeniedException` → `403 Forbidden`
- `AuthenticationException` → `401 Unauthorized`
- `MethodArgumentNotValidException` → `400 Bad Request` (inherited from `ResponseEntityExceptionHandler`)

This handler is active in every microservice that has `order-utils` on the classpath.
Do NOT create a second `@ControllerAdvice` unless you have a service-specific exception that
`order-utils` cannot handle. If you do, extend `ResourceExceptionHandler`.

---

## ErrorView Structure

```java
public record ErrorView(
    LocalDateTime timestamp,
    String msg,
    HttpStatus status,
    String error,
    String path,
    Class<?> clazz
) {}
```

All error responses use this structure. Never return a raw String or a custom map.

Example response for a 404:
```json
{
  "timestamp": "2025-03-19T10:30:00",
  "msg": "Resource not found",
  "status": "NOT_FOUND",
  "error": "Not Found",
  "path": "order-id-abc123",
  "class": "org.cedro.orderutils.infra.exception.ResourceNotFoundException"
}
```

---

## Throwing ResourceNotFoundException

`ResourceNotFoundException` is the ONLY exception to throw when an entity is not found.
Never return `ResponseEntity.notFound().build()` as the final behavior — let the exception
propagate to `ResourceExceptionHandler`.

The base `AbstractService.update()` currently returns `ResponseEntity.notFound().build()` for
missing entities. Override it in service implementations that require proper error responses:

```java
@Override
public ResponseEntity<Order> update(String id, Order order) {
    return orderRepository.findById(id)
        .map(existing -> {
            order.setId(id); // ensure ID is preserved
            Order updated = orderRepository.save(order);
            log.info("Order {} updated", id);
            return ResponseEntity.ok(updated);
        })
        .orElseThrow(() -> new ResourceNotFoundException(new ErrorView(
            LocalDateTime.now(),
            "Order not found for update",
            HttpStatus.NOT_FOUND,
            "Not Found",
            id,
            Order.class
        )));
}
```

---

## HTTP Status Code Rules

| Scenario                              | HTTP Status |
|---------------------------------------|-------------|
| Entity not found                      | 404         |
| Invalid request body / missing fields | 400         |
| Insufficient permissions              | 403         |
| Missing or invalid JWT token          | 401         |
| Successful GET with results           | 200         |
| Successful GET with empty results     | 204         |
| Successful POST / save                | 200 (current pattern) |
| Unexpected infrastructure failure     | 500 (last resort only) |

Rules:
- NEVER return 500 for a business logic error. 500 means something broke unexpectedly.
- NEVER return 200 with an error message in the body.
- NEVER return 404 as a successful "empty list" — use 204 for empty lists.
- The base `AbstractService.list()` already returns 204 for empty results.

---

## Kafka Consumer Error Handling

Consumer errors are isolated. Never let a consumer exception propagate:

```java
@KafkaListener(topics = "payment-processed", containerFactory = "orderPaymentListenerContainerFactory")
public void onPaymentProcessed(String payload) {
    try {
        JsonNode node = objectMapper.readTree(payload);
        String orderId = node.path("orderId").asText();
        String status = node.path("status").asText();
        // ... process ...
    } catch (JsonProcessingException e) {
        log.error("Invalid JSON in payment-processed message. Payload: {}", payload, e);
        // Log and discard — do not rethrow
    } catch (Exception e) {
        log.error("Unexpected error processing payment event. Payload: {}", payload, e);
        // Log and discard — do not rethrow
    }
}
```

If an exception is rethrown from a `@KafkaListener`, Spring Kafka will retry the message
indefinitely with the default error handler, causing an infinite loop.

For messages that genuinely cannot be processed, log with `log.error(...)` at ERROR level,
include the full payload in the log, and move on. A dead-letter queue (DLQ) can be added later.

---

## Validation Error Handling

`MethodArgumentNotValidException` is handled automatically by `ResponseEntityExceptionHandler`
(which `ResourceExceptionHandler` extends). The response returns 400 with field-level error details.

To trigger validation:
1. Annotate entity fields with `@NotBlank`, `@NotNull`, `@Positive`, `@Valid`, etc.
2. Use `@Valid` on the `@RequestBody` parameter in the controller.
3. For nested objects (e.g., `OrderItem` inside `Order.items`), use `@Valid` on the field:

```java
@NotEmpty(message = "Order must have at least one item")
@Valid
private List<OrderItem> items;
```

This triggers validation on each `OrderItem`'s `@NotBlank`/`@NotNull` fields automatically.

---

## Stack Trace Policy

- NEVER let stack traces reach HTTP responses.
- `ResourceExceptionHandler` returns structured `ErrorView` — no stack traces.
- Spring Boot's default error handling (`/error` endpoint) may expose stack traces in dev.
  For production, set: `server.error.include-stacktrace=never` in `application-prod.yml`.

---

## Service-Level vs Gateway-Level Errors

- Authentication errors (401) occur at both gateway and service level.
  The gateway rejects invalid tokens first; services provide a second validation layer.
- Authorization errors (403) occur at the service level (`@PreAuthorize` evaluation).
  The gateway does not evaluate roles — it only validates the JWT signature.
- This is by design (defense in depth). Do not attempt to consolidate error handling to one layer.

---

## Logging Error Correlation

When logging errors, always include:
1. The entity ID (orderId, productId, etc.) if available.
2. The full payload for Kafka consumer errors.
3. The exception as the last argument to SLF4J so the stack trace is captured in logs.

```java
// Correct
log.error("Failed to process payment event for order {}. Payload: {}", orderId, payload, e);

// Missing exception — stack trace lost
log.error("Failed to process payment event. Payload: {}", payload);
```
