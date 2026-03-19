# Skill: Create Order

## Purpose

Implement the full order creation flow: validate input, persist to MongoDB, publish to
`vendas-topico` Kafka topic, and return HTTP 200.

## When to Use

- Adding a new `POST /orderflow/v1/order` endpoint behavior.
- Modifying order creation logic (pre-save validation, enrichment, etc.).
- Debugging why orders are not being created or not reaching Kafka.

## Prerequisites

- `order-model` entity `Order`, `OrderItem`, `Metadata` exist.
- `OrderRepository extends MongoRepository<Order, String>` exists.
- Kafka topic `vendas-topico` exists (created by Docker Compose).
- `KafkaTemplate<String, String>` bean is configured.

## Knowledge References

- `.agents/knowledge/orderflow/pedidos.md` — Order entity structure
- `.agents/knowledge/spring/kafka.md` — KafkaTemplate usage
- `.agents/knowledge/spring/validation.md` — @Valid, @NotBlank on Order fields
- `.agents/rules/security-rules.md` — @PreAuthorize for order creation

---

## Steps

### Step 1: Verify Required Request Body Fields

The Order entity in `order-model` already has Jakarta Validation annotations. The client MUST send:

```json
{
    "customerId": "customer-uuid-123",
    "customerName": "João da Silva",
    "items": [
        {
            "productId": "product-abc",
            "productName": "Notebook",
            "quantity": 2,
            "unitPrice": 3500.00
        }
    ],
    "totalAmount": 7000.00
}
```

Fields NOT sent by the client (set by the system):
- `id` — MongoDB generates
- `status` — leave null (set by listener)
- `approvalStatus` — leave null
- `approvedBy` — leave null
- `approvalDate` — leave null
- `metadata` — auto-populated by `OrderMetadataListener`

### Step 2: Verify OrderController has @PreAuthorize on save

```java
@RestController
@RequestMapping("/orderflow/v1/order")
public class OrderController extends AbstractController<Order> {

    private final OrderServiceImpl orderService;

    public OrderController(OrderServiceImpl orderService) {
        this.orderService = orderService;
    }

    @Override
    protected AbstractService<Order> getService() {
        return orderService;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER')")  // BUYER can create orders
    public ResponseEntity<Order> save(Order value, String returnEntity) {
        return super.save(value, returnEntity);
    }

    // ... other overrides ...
}
```

### Step 3: Verify OrderServiceImpl publishes to Kafka after save

```java
@Service
public class OrderServiceImpl extends AbstractService<Order> implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final String VENDAS_TOPICO = "vendas-topico";

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderServiceImpl(OrderRepository orderRepository,
                            KafkaTemplate<String, String> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public ResponseEntity<Order> save(Order order, String returnEntity) {
        Order saved = orderRepository.save(order);
        String orderId = saved.getId();
        log.info("Order {} saved. Publishing to {}", orderId, VENDAS_TOPICO);
        kafkaTemplate.send(VENDAS_TOPICO, orderId, orderId);
        log.info("Order {} published to {}", orderId, VENDAS_TOPICO);
        return ResponseEntity.ok(saved);
    }

    @Override
    protected MongoRepository<Order, String> getRepository() {
        return orderRepository;
    }

    @Override
    protected Class<Order> getEntityClass() {
        return Order.class;
    }
}
```

### Step 4: Verify Metadata is auto-populated

If `OrderMetadataListener` is not present, add it:

```java
@Component
public class OrderMetadataListener extends AbstractMongoEventListener<Order> {

    @Override
    public void onBeforeSave(BeforeSaveEvent<Order> event) {
        Order order = event.getSource();
        Metadata meta = order.getMetadata() != null ? order.getMetadata() : new Metadata();

        if (meta.getCreatedAt() == null) {
            meta.setCreatedAt(LocalDateTime.now());
            meta.setCreatedBy(SecurityContextHelper.getCurrentUsername());
            meta.setVersion(1L);
        } else {
            meta.setVersion(meta.getVersion() != null ? meta.getVersion() + 1 : 1L);
        }
        meta.setUpdatedAt(LocalDateTime.now());
        meta.setUpdatedBy(SecurityContextHelper.getCurrentUsername());
        if (meta.getDeleted() == null) meta.setDeleted(false);

        order.setMetadata(meta);
    }
}
```

### Step 5: Test the flow

1. Start `eureka-server`, `order-security-server`, `order-service`.
2. Obtain JWT:
   ```bash
   curl -X POST http://localhost:8080/order-security-server/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"buyer1","password":"buyer123"}'
   ```
3. Create order:
   ```bash
   curl -X POST http://localhost:8080/order-service/orderflow/v1/order \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{
       "customerId": "cust-001",
       "customerName": "Test Customer",
       "items": [{"productId":"p1","productName":"Item","quantity":1,"unitPrice":100.00}],
       "totalAmount": 100.00
     }'
   ```
4. Verify in Kafdrop (`http://localhost:9000`) that `vendas-topico` received the message.
5. Verify in MongoDB that the order was saved with `metadata.createdAt` populated.

---

## Validation Checklist

- [ ] Order entity has `@NotBlank` on `customerId`, `customerName`
- [ ] Order entity has `@NotEmpty @Valid` on `items`
- [ ] Order entity has `@NotNull @Positive` on `totalAmount`
- [ ] `OrderController.save()` has `@PreAuthorize("hasAnyAuthority('ADMIN','MANAGER','BUYER')")`
- [ ] `OrderServiceImpl.save()` calls `kafkaTemplate.send(VENDAS_TOPICO, orderId, orderId)` after save
- [ ] `OrderServiceImpl.getEntityClass()` returns `Order.class`
- [ ] Log statements present before and after Kafka send

## Common Mistakes

- Forgetting `@PreAuthorize` on `save()` — method inherits no authorization, endpoint is unprotected.
- Publishing to Kafka before saving — if save fails, an event for a non-existent order is published.
- Using `returnEntity` query param for redirect logic — the current `AbstractService` ignores it.
- Not implementing `getEntityClass()` — causes compile error.
- Setting `status` in the request body — status should be set by the system, not the client.
