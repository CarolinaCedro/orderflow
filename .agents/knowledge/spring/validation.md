# Jakarta Validation — OrderFlow Reference

Spring Boot 3 uses Jakarta Validation (formerly javax.validation). All entities in `order-model`
use validation annotations. Validation is triggered by `@Valid` in controller methods.

---

## How Validation Works

1. Controller receives `@RequestBody` with `@Valid`.
2. Spring invokes Jakarta Validator on the object.
3. If any constraint fails, Spring throws `MethodArgumentNotValidException`.
4. `ResourceExceptionHandler` (extends `ResponseEntityExceptionHandler`) intercepts it and
   returns HTTP 400 with field-level error messages.

The validation errors are automatic — no custom code needed beyond the annotations.

---

## Entity Annotations

### Order Entity

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
public class Order {

    @Id
    private String id;

    @NotBlank(message = "customerId is required")
    private String customerId;

    @NotBlank(message = "customerName is required")
    private String customerName;

    @NotEmpty(message = "Order must have at least one item")  // list must not be empty
    @Valid                                                     // validates each OrderItem
    private List<OrderItem> items;

    @NotNull(message = "totalAmount is required")
    @Positive(message = "totalAmount must be positive")
    private BigDecimal totalAmount;

    // status, approvalStatus — no validation (set by the system, not the client)
    private OrderStatus status;
    private ApprovalStatus approvalStatus;
    private String approvedBy;
    private LocalDateTime approvalDate;
    private String erpOrderId;
    private Metadata metadata;  // populated by OrderMetadataListener
}
```

### OrderItem Entity

```java
@Getter
@Setter
@ToString
public class OrderItem {

    @NotBlank(message = "productId is required")
    private String productId;

    @NotBlank(message = "productName is required")
    private String productName;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be positive")
    private Integer quantity;

    @NotNull(message = "unitPrice is required")
    @Positive(message = "unitPrice must be positive")
    private BigDecimal unitPrice;

    private BigDecimal totalPrice;  // Computed, not validated
}
```

### Product Entity

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    @NotBlank(message = "category is required")
    private String category;

    @NotNull(message = "price is required")
    @Positive(message = "price must be positive")
    private BigDecimal price;

    @NotNull(message = "stockQuantity is required")
    @Min(value = 0, message = "stockQuantity cannot be negative")
    private Integer stockQuantity;

    private String imageUrl;
    private Boolean isActive = true;
}
```

---

## Annotation Reference

| Annotation            | Applied to        | Behavior                                         |
|-----------------------|-------------------|--------------------------------------------------|
| `@NotNull`            | Any object        | Field must not be null                           |
| `@NotBlank`           | String            | Must not be null, empty, or whitespace only      |
| `@NotEmpty`           | String, Collection| Must not be null or empty (whitespace ok for String)|
| `@Positive`           | Number            | Must be > 0                                      |
| `@PositiveOrZero`     | Number            | Must be >= 0                                     |
| `@Min(value)`         | Number            | Must be >= value                                 |
| `@Max(value)`         | Number            | Must be <= value                                 |
| `@Size(min, max)`     | String, Collection| Length must be within range                      |
| `@Email`              | String            | Must be a valid email address                    |
| `@Pattern(regexp)`    | String            | Must match the regex                             |
| `@Valid`              | Object, Collection| Triggers cascade validation on nested objects    |
| `@Validated`          | Class, Method     | Enables method-level validation (Spring)         |

---

## Nested Validation with @Valid

The `@Valid` annotation on a collection or nested object triggers validation recursively:

```java
// On the Order class:
@NotEmpty(message = "Order must have at least one item")
@Valid                     // This triggers validation of each OrderItem
private List<OrderItem> items;
```

Without `@Valid`, the `@NotBlank` and `@NotNull` on `OrderItem` fields would not fire.
The list being non-empty (`@NotEmpty`) is checked first; each item's fields are checked second.

---

## Controller-Level @Valid

The `Rest<T>` interface declares:

```java
public interface Rest<T> {
    ResponseEntity<T> save(@Valid @RequestBody T value, @RequestParam(required = false) String returnEntity);
    ResponseEntity<T> update(@PathVariable String id, @Valid @RequestBody T model);
    // ...
}
```

Since `AbstractController` delegates to `AbstractService` which implements `Rest<T>`, and the
`Rest` interface already has `@Valid`, validation fires automatically on `save` and `update`.

For custom endpoints added to a controller, always add `@Valid @RequestBody`:

```java
@PostMapping("/bulk")
@PreAuthorize("hasAuthority('ADMIN')")
public ResponseEntity<List<Order>> bulkSave(@Valid @RequestBody List<@Valid Order> orders) {
    // ...
}
```

---

## Validation Error Response

When validation fails, Spring returns:

```json
{
  "timestamp": "2025-03-19T10:30:00.000+00:00",
  "status": 400,
  "errors": [
    {
      "field": "customerId",
      "message": "customerId is required",
      "rejectedValue": null
    },
    {
      "field": "totalAmount",
      "message": "totalAmount must be positive",
      "rejectedValue": -50
    }
  ]
}
```

This is handled automatically by `ResponseEntityExceptionHandler.handleMethodArgumentNotValid()`.

---

## Service-Level Validation

For business rule validation that cannot be expressed as a constraint annotation:

```java
@Service
public class OrderServiceImpl extends AbstractService<Order> implements OrderService {

    @Override
    public ResponseEntity<Order> save(Order order, String returnEntity) {
        // Business rule: totalAmount must equal sum of item totals
        BigDecimal expectedTotal = order.getItems().stream()
            .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (order.getTotalAmount().compareTo(expectedTotal) != 0) {
            throw new IllegalArgumentException(
                "totalAmount %s does not match item total %s".formatted(
                    order.getTotalAmount(), expectedTotal)
            );
        }
        // ... save ...
    }
}
```

For the `IllegalArgumentException` to return 400, add a handler to `ResourceExceptionHandler`:

```java
@ExceptionHandler
private ResponseEntity<ErrorView> handleIllegalArgument(IllegalArgumentException error) {
    ErrorView view = new ErrorView(LocalDateTime.now(), error.getMessage(),
        HttpStatus.BAD_REQUEST, "Bad Request", error.getMessage(), error.getClass());
    return ResponseEntity.badRequest().body(view);
}
```

---

## Custom Constraints

If a business rule is complex and reusable, create a custom constraint:

```java
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCepValidator.class)
public @interface ValidCep {
    String message() default "Invalid CEP format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class ValidCepValidator implements ConstraintValidator<ValidCep, String> {
    private static final Pattern CEP_PATTERN = Pattern.compile("\\d{5}-?\\d{3}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || CEP_PATTERN.matcher(value).matches();
    }
}
```

Usage:
```java
@ValidCep
private String cep;
```
