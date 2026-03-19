# Exception Handling — OrderFlow

Exception handling in OrderFlow is centralized in `order-utils`. This document covers the
hierarchy, usage patterns, and anti-patterns.

---

## Exception Hierarchy

```
RuntimeException
└── ResourceNotFoundException  (order-utils — use for all 404 scenarios)

Spring Security:
├── AccessDeniedException      (403 — Spring Security throws this for @PreAuthorize failures)
└── AuthenticationException    (401 — Spring Security throws this for invalid/missing JWT)

Spring MVC:
└── MethodArgumentNotValidException  (400 — thrown automatically for @Valid failures)
```

All three Spring exceptions are handled by `ResourceExceptionHandler` in `order-utils`.

---

## ResourceNotFoundException

Located at: `org.cedro.orderutils.infra.exception.ResourceNotFoundException`

```java
public class ResourceNotFoundException extends RuntimeException {

    private final String error;
    private final String path;

    public ResourceNotFoundException(ErrorView errorView) {
        super(errorView.getMsg());
        this.error = errorView.getError();
        this.path = errorView.getPath();
    }
}
```

How to throw it correctly:

```java
import org.cedro.orderutils.infra.exception.ResourceNotFoundException;
import org.cedro.orderutils.infra.model.ErrorView;
import org.springframework.http.HttpStatus;

// In a service method:
public Order getOrderById(String id) {
    return orderRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(new ErrorView(
            LocalDateTime.now(),
            "Order not found",         // msg — shown to client
            HttpStatus.NOT_FOUND,      // status
            "Not Found",               // error label
            id,                        // path — entity ID or request path
            Order.class                // class — for debugging
        )));
}
```

---

## ErrorView

Located at: `org.cedro.orderutils.infra.model.ErrorView`

```java
// The fields (getter-based class in current codebase):
LocalDateTime timestamp
String msg
HttpStatus status
String error
String path      // used for entity ID or the request path that caused the error
Class<?> clazz   // the entity class or exception class
```

Always construct `ErrorView` at the throw site, not at the handler site. The thrower has
the most context.

---

## ResourceExceptionHandler

Located at: `org.cedro.orderutils.infra.handler.ResourceExceptionHandler`

This `@ControllerAdvice` intercepts:

```java
@ExceptionHandler
private ResponseEntity<ErrorView> handleResourceNotFoundException(ResourceNotFoundException error) {
    // Returns 404 with ErrorView
}

@ExceptionHandler
private ResponseEntity<ErrorView> handleAccessDeniedException(AccessDeniedException error) {
    // Returns 403 with ErrorView
}

@ExceptionHandler
private ResponseEntity<ErrorView> handleAuthenticationException(AuthenticationException error) {
    // Returns 401 with ErrorView
}
```

`ResponseEntityExceptionHandler` (extended by `ResourceExceptionHandler`) handles
`MethodArgumentNotValidException` → returns 400 with field-level validation errors.

---

## How @ControllerAdvice Intercepts

Spring MVC's exception resolution order:
1. `ExceptionHandlerExceptionResolver` — checks `@ExceptionHandler` in controllers, then in
   `@ControllerAdvice` beans.
2. `ResourceExceptionHandler` is discovered as a `@ControllerAdvice` and registers its
   `@ExceptionHandler` methods globally.
3. When `ResourceNotFoundException` is thrown anywhere in a `@RestController`, Spring routes it
   to `ResourceExceptionHandler.handleResourceNotFoundException()`.

This works because `order-utils` is on the classpath of every microservice and Spring's component
scan picks up the `@ControllerAdvice` automatically.

---

## Anti-Patterns

### 1. Returning ResponseEntity.notFound() instead of throwing
```java
// WRONG — bypasses ResourceExceptionHandler, returns empty body
return orderRepository.findById(id)
    .map(ResponseEntity::ok)
    .orElseGet(() -> ResponseEntity.notFound().build());

// CORRECT — ResourceExceptionHandler returns structured ErrorView
return orderRepository.findById(id)
    .map(ResponseEntity::ok)
    .orElseThrow(() -> new ResourceNotFoundException(new ErrorView(
        LocalDateTime.now(), "Order not found", HttpStatus.NOT_FOUND, "Not Found", id, Order.class
    )));
```

Note: The base `AbstractService.findById()` still uses `ResponseEntity.notFound().build()`.
Override it in your service impl if you need structured error responses.

### 2. Throwing RuntimeException directly
```java
// WRONG — no structured error body for the client
throw new RuntimeException("Order not found: " + id);

// CORRECT
throw new ResourceNotFoundException(new ErrorView(...));
```

### 3. Swallowing exceptions silently
```java
// WRONG — error is lost
try {
    process(payload);
} catch (Exception e) {
    // empty catch
}

// CORRECT — log it
try {
    process(payload);
} catch (Exception e) {
    log.error("Failed to process payload: {}", payload, e);
}
```

### 4. Catching and rethrowing in Kafka consumers
```java
// WRONG — causes infinite retry
@KafkaListener(topics = "vendas-topico", groupId = "payment-group")
public void process(String orderId) {
    try {
        doProcess(orderId);
    } catch (Exception e) {
        throw new RuntimeException("Processing failed", e); // Rethrows → retry loop
    }
}

// CORRECT — catch, log, move on
@KafkaListener(topics = "vendas-topico", groupId = "payment-group")
public void process(String orderId) {
    try {
        doProcess(orderId);
    } catch (Exception e) {
        log.error("Failed to process order {}. Will not retry.", orderId, e);
    }
}
```

### 5. Exposing stack traces in responses
```java
// WRONG — returns raw exception message with stack trace
return ResponseEntity.status(500).body(e.toString());

// CORRECT — let ResourceExceptionHandler format the response
// Or throw ResourceNotFoundException and let the handler work
```

---

## Adding a New Exception Type

If a new business exception is needed (e.g., `OrderAlreadyCompletedException`):

1. Define it in `order-utils` if it could be reused across services.
2. Extend `RuntimeException`, not `Exception` (avoids checked exception boilerplate).
3. Add a handler method in `ResourceExceptionHandler`.
4. Map it to the appropriate HTTP status (422 Unprocessable Entity for business rule violations).

```java
// In order-utils
public class OrderAlreadyCompletedException extends RuntimeException {
    public OrderAlreadyCompletedException(String orderId) {
        super("Order " + orderId + " is already in COMPLETED status");
    }
}

// In ResourceExceptionHandler
@ExceptionHandler
private ResponseEntity<ErrorView> handleOrderAlreadyCompleted(OrderAlreadyCompletedException error) {
    ErrorView view = new ErrorView(
        LocalDateTime.now(),
        error.getMessage(),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Unprocessable Entity",
        error.getLocalizedMessage(),
        error.getClass()
    );
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(view);
}
```
