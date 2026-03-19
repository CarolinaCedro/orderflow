# Coding Standards — OrderFlow

All code produced for this project MUST conform to these standards without exception.

---

## Package Structure

Every service follows the same package hierarchy. Replace `{servicename}` with the service name
(e.g., `orderservice`, `inventoryservice`, `paymentservice`).

```
org.cedro.{servicename}/
├── controller/          # REST controllers extending AbstractController
├── service/             # Service interfaces
├── service/impl/        # Service implementations extending AbstractService
├── repository/          # MongoRepository interfaces
├── config/              # Spring @Configuration classes (Kafka, Mongo, etc.)
├── listeners/           # @KafkaListener components and Spring ApplicationListener
├── init/                # ApplicationRunner / CommandLineRunner for data seeding
└── events/              # Custom ApplicationEvent subclasses (if needed)
```

Shared library packages:
- `com.cedro.orderrestservice.rest.*` — AbstractController, AbstractService, Rest interface
- `org.cedro.ordermodel.model.*` — all domain entities
- `org.cedro.orderutils.*` — security, exceptions, error handling, Feign clients

---

## Logging

- **Always** use SLF4J. `System.out.println` is prohibited.
- Declare logger as a `private static final` field:

```java
private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
```

- Log levels:
  - `log.info(...)` — successful operations, state transitions, Kafka send/receive
  - `log.warn(...)` — non-critical anomalies (e.g., payment rejected, order not found for update)
  - `log.error(...)` — caught exceptions in consumers, unexpected failures
  - `log.debug(...)` — query parameters, internal state (only enable in dev)

- Always include the entity ID in log messages:

```java
log.info("Order {} saved and published to {}", orderId, VENDAS_TOPICO);
log.error("Failed to process payment event. Payload: {}", payload, e);
```

---

## Dependency Injection

- **Constructor injection always** in `@Service`, `@Component`, `@RestController` classes.
- Never use `@Autowired` on fields in new code.
- If a class has a single constructor, `@Autowired` is optional (Spring injects automatically):

```java
// Correct
@Service
public class OrderServiceImpl extends AbstractService<Order> implements OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderServiceImpl(OrderRepository orderRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }
}

// Forbidden
@Service
public class OrderServiceImpl {
    @Autowired
    private OrderRepository orderRepository; // Never do this
}
```

---

## Entity Design

Every domain entity MUST have:

```java
@Data               // Lombok: getters, setters, equals, hashCode, toString
@NoArgsConstructor  // Required by Spring Data MongoDB for deserialization
@AllArgsConstructor // Convenience constructor for testing
@Document(collection = "collection_name")
public class Product {

    @Id
    private String id;

    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "price is required")
    @Positive(message = "price must be positive")
    private BigDecimal price;

    @NotNull(message = "stockQuantity is required")
    @Min(value = 0, message = "stockQuantity cannot be negative")
    private Integer stockQuantity;

    private Metadata metadata; // Always include for soft delete + audit
}
```

Rules:
- All required fields MUST have `@NotBlank` (String) or `@NotNull` (objects/numbers).
- Numeric fields with business constraints MUST have `@Positive` or `@Min`.
- Collections that must not be empty MUST have `@NotEmpty` + `@Valid` for nested validation.
- Every entity that is persisted MUST include a `Metadata metadata` field.
- Only use `@Data` — never use `@Getter`/`@Setter` separately unless you have a reason (e.g., `OrderItem`).

---

## AbstractService Extension

Any service backing a REST endpoint MUST extend `AbstractService<T>`:

```java
@Service
public class ProductServiceImpl extends AbstractService<Product> implements ProductService {

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    protected MongoRepository<Product, String> getRepository() {
        return productRepository;
    }

    @Override
    protected Class<Product> getEntityClass() {   // ALWAYS override this — no default exists
        return Product.class;
    }
}
```

`getEntityClass()` is abstract — forgetting it causes a compile error. Always implement it.

To add custom behavior, override the inherited methods:

```java
@Override
public ResponseEntity<Order> save(Order order, String returnEntity) {
    // Custom pre-save logic
    Order saved = orderRepository.save(order);
    kafkaTemplate.send(VENDAS_TOPICO, saved.getId(), saved.getId());
    log.info("Order {} saved and published to {}", saved.getId(), VENDAS_TOPICO);
    return ResponseEntity.ok(saved);
}
```

---

## AbstractController Extension

Every REST controller MUST extend `AbstractController<T>` and override **every** method with
`@PreAuthorize`:

```java
@RestController
@RequestMapping("/orderflow/v1/product")
public class ProductController extends AbstractController<Product> {

    private final ProductServiceImpl productService;

    public ProductController(ProductServiceImpl productService) {
        this.productService = productService;
    }

    @Override
    protected AbstractService<Product> getService() {
        return productService;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<Product> save(Product value, String returnEntity) {
        return super.save(value, returnEntity);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<Product> update(String id, Product model) {
        return super.update(id, model);
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void deleteById(String id) {
        super.deleteById(id);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<Product> findById(String id) {
        return super.findById(id);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<List<Product>> list(Map<String, String> allRequestParams) {
        return super.list(allRequestParams);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<Long> count(Map<String, String> allRequestParams) {
        return super.count(allRequestParams);
    }
}
```

Rules:
- Never expose a controller method without `@PreAuthorize`.
- `@PreAuthorize` expressions use `hasAuthority(...)` with the exact role name (no `ROLE_` prefix).
- `ADMIN` always gets full access. `VIEWER` always gets read-only access.

---

## Kafka Coding Standards

Always define topic names as constants:

```java
private static final String VENDAS_TOPICO = "vendas-topico";
private static final String PAYMENT_PROCESSED_TOPIC = "payment-processed";
```

Logging pattern for producers:
```java
log.info("Publishing order {} to topic {}", orderId, VENDAS_TOPICO);
kafkaTemplate.send(VENDAS_TOPICO, orderId, orderId);
log.info("Order {} successfully published to {}", orderId, VENDAS_TOPICO);
```

Logging pattern for consumers:
```java
@KafkaListener(topics = "vendas-topico", groupId = "payment-group")
public void processarVenda(String orderId) {
    log.info("Order received for payment processing: orderId={}", orderId);
    // ... process ...
    log.info("Payment processed: orderId={}, status={}", orderId, status);
}
```

Exception handling in consumers — ALWAYS catch, NEVER rethrow:
```java
@KafkaListener(topics = "payment-processed", containerFactory = "orderPaymentListenerContainerFactory")
public void onPaymentProcessed(String payload) {
    try {
        // processing logic
    } catch (Exception e) {
        log.error("Failed to process payment event. Payload: {}", payload, e);
        // Do NOT rethrow — causes infinite retry
    }
}
```

---

## MongoDB Query Standards

- `MongoRepository`: only for simple, named queries with fixed criteria.
- `MongoTemplate` + `Criteria`: for any query that uses user-provided parameters or dynamic filters.

Always include the soft-delete filter when building queries manually:

```java
Query query = new Query(Criteria.where("metadata.deleted").ne(true));
// The base AbstractService.buildQuery() does this automatically for list/count
```

Never use raw string field names without verifying against the entity class. Use the field path
exactly as it appears in the MongoDB document (camelCase, nested with dot notation).

---

## Validation

All incoming request bodies MUST be annotated with `@Valid` in the controller method:

The `Rest` interface already declares `@Valid` on the `save` and `update` methods. Do not remove it.
When creating custom endpoints, always add `@Valid @RequestBody`.

```java
@PostMapping("/custom")
public ResponseEntity<Order> customSave(@Valid @RequestBody Order order) {
    // ...
}
```

---

## No Dead Code / No TODOs in Committed Code

- Remove all `// TODO` comments before committing.
- Remove all unused imports.
- Remove all unused fields and methods.
- Never commit commented-out code blocks.
