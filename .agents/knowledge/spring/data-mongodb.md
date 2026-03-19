# Spring Data MongoDB — OrderFlow Reference

MongoDB 7.x with Spring Data MongoDB. URI: `mongodb://root:products@localhost:27018/orderflow?authSource=admin`

---

## MongoRepository vs MongoTemplate

| Use case                                      | Use                  |
|-----------------------------------------------|----------------------|
| Find by single field with fixed value          | MongoRepository      |
| Find by username, find by ID                   | MongoRepository      |
| Dynamic filters from request params            | MongoTemplate        |
| Queries with multiple optional criteria        | MongoTemplate        |
| Pagination with custom criteria                | MongoTemplate        |
| Projections (partial document fetch)           | MongoTemplate        |
| Aggregation pipelines                          | MongoTemplate        |
| Soft delete (update + query combination)        | MongoTemplate        |

Rule: `MongoRepository` for simple, named, fixed queries. `MongoTemplate` for everything else.

---

## MongoRepository

```java
// In order-security-server
public interface AppUserRepository extends MongoRepository<AppUser, String> {
    Optional<AppUser> findByUsername(String username);
}

// In order-service
public interface OrderRepository extends MongoRepository<Order, String> {
    // findById, save, existsById are inherited
}

// In inventory-service
public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByCategoryAndIsActive(String category, boolean isActive);
}
```

---

## AbstractService.buildQuery() — The Base Dynamic Query

Every `list()` and `count()` call goes through this method in `AbstractService`:

```java
private Query buildQuery(Map<String, String> params) {
    Query query = new Query(Criteria.where("metadata.deleted").ne(true));
    params.entrySet().stream()
        .filter(e -> !RESERVED_PARAMS.contains(e.getKey()))
        .forEach(e -> query.addCriteria(Criteria.where(e.getKey()).is(e.getValue())));
    return query;
}
```

Reserved params excluded from criteria: `page`, `size`, `sort`, `returnEntity`.

Calling `GET /orderflow/v1/order?status=PENDING_APPROVAL&customerId=abc123` translates to:
```java
Criteria.where("metadata.deleted").ne(true)
    .and("status").is("PENDING_APPROVAL")
    .and("customerId").is("abc123")
```

---

## Building Dynamic Queries Manually

For complex queries beyond the base pattern:

```java
public List<Order> findOrdersByFilters(String customerId, OrderStatus status,
                                        LocalDateTime from, LocalDateTime to) {
    Query query = new Query();

    // Always include soft-delete filter
    Criteria criteria = Criteria.where("metadata.deleted").ne(true);

    if (customerId != null && !customerId.isBlank()) {
        criteria = criteria.and("customerId").is(customerId);
    }
    if (status != null) {
        criteria = criteria.and("status").is(status);
    }
    if (from != null && to != null) {
        criteria = criteria.and("metadata.createdAt").gte(from).lte(to);
    }

    query.addCriteria(criteria);
    query.with(Sort.by(Sort.Direction.DESC, "metadata.createdAt"));

    return mongoTemplate.find(query, Order.class);
}
```

---

## Soft Delete Pattern with Metadata

`Metadata` class contains the soft-delete fields:

```java
@Data
public class Metadata {
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private String deletedBy;
    private LocalDateTime deletedAt;
    private Boolean deleted = false;
    private String correlationId;
    private String tenantId;
    private Long version;
}
```

Correct soft-delete implementation in a service:

```java
@Override
public void deleteById(String id) {
    orderRepository.findById(id).ifPresentOrElse(order -> {
        Metadata meta = order.getMetadata();
        if (meta == null) meta = new Metadata();
        meta.setDeleted(true);
        meta.setDeletedAt(LocalDateTime.now());
        meta.setDeletedBy(SecurityContextHelper.getCurrentUsername());
        order.setMetadata(meta);
        orderRepository.save(order);
        log.info("Order {} soft-deleted by {}", id, meta.getDeletedBy());
    }, () -> {
        throw new ResourceNotFoundException(new ErrorView(
            LocalDateTime.now(), "Order not found",
            HttpStatus.NOT_FOUND, "Not Found", id, Order.class
        ));
    });
}
```

All queries use `Criteria.where("metadata.deleted").ne(true)` — both `false` and `null` values
match this criteria, so records without a `metadata` field or with `deleted: null` are included
in results. Only records with `deleted: true` are excluded.

---

## @Document Annotation

```java
@Document(collection = "orders")     // Explicit collection name — always specify
@Document(collection = "products")
@Document(collection = "users")      // AppUser collection
```

Without `collection = "..."`, Spring Data uses the lowercase class name. Always be explicit to
avoid surprises when class names change.

Define collection name as a constant when the same value is needed elsewhere:

```java
@Document(collection = Order.ORDERS_COLLECTION)
public class Order {
    public static final String ORDERS_COLLECTION = "orders";
    ...
}
```

---

## @Id Annotation

MongoDB `_id` field maps to a Java field annotated with `@Id`:

```java
@Id
private String id;  // String is preferred — MongoDB ObjectId stored as string
```

If `id` is null when saving, MongoDB generates a new ObjectId and sets it. If `id` is provided,
MongoDB uses it (upsert behavior with `save()`).

---

## Metadata Auto-Population (OrderMetadataListener Pattern)

Automatically set `createdAt`, `updatedAt`, `createdBy` before save using a MongoDB event listener:

```java
// In order-service: org.cedro.orderservice.config.mongo.OrderMetadataListener
@Component
public class OrderMetadataListener extends AbstractMongoEventListener<Order> {

    @Override
    public void onBeforeSave(BeforeSaveEvent<Order> event) {
        Order order = event.getSource();
        Metadata meta = order.getMetadata();
        if (meta == null) {
            meta = new Metadata();
            order.setMetadata(meta);
        }

        if (meta.getCreatedAt() == null) {
            meta.setCreatedAt(LocalDateTime.now());
            meta.setCreatedBy(SecurityContextHelper.getCurrentUsername());
        }
        meta.setUpdatedAt(LocalDateTime.now());
        meta.setUpdatedBy(SecurityContextHelper.getCurrentUsername());
    }
}
```

This pattern is used instead of `@PrePersist` (which is JPA-specific) and instead of
`@CreatedDate`/`@LastModifiedDate` (which require `@EnableMongoAuditing` and `@Document` with
auditing annotations — functional but this project chose the listener pattern).

---

## MongoTemplate Configuration

`MongoTemplate` is autoconfigured by Spring Boot when `spring-boot-starter-data-mongodb` is on
the classpath and `spring.data.mongodb.uri` is set. No additional `@Bean` needed for standard use.

For custom configuration (e.g., custom converters):

```java
@Configuration
public class MongoDbConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
            // Custom type converters if needed
        ));
    }
}
```

The `inventory-service` has a `MongoDbConfig` class — check it for any custom converters.

---

## Pagination with MongoTemplate

```java
public Page<Order> findOrdersPaged(Map<String, String> params, int page, int size) {
    Query query = new Query(Criteria.where("metadata.deleted").ne(true));
    // add criteria from params...

    long total = mongoTemplate.count(query, Order.class);

    query.with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "metadata.createdAt")));
    List<Order> content = mongoTemplate.find(query, Order.class);

    return new PageImpl<>(content, PageRequest.of(page, size), total);
}
```

Note: `AbstractService.list()` does not currently implement pagination. Implement it in the
service override when needed.

---

## Index Recommendations

Run in MongoDB shell or via migration:
```javascript
// orders collection
db.orders.createIndex({ "metadata.deleted": 1, "status": 1 })
db.orders.createIndex({ "customerId": 1, "metadata.deleted": 1 })
db.orders.createIndex({ "metadata.createdAt": -1 })

// products collection
db.products.createIndex({ "metadata.deleted": 1, "category": 1, "isActive": 1 })

// users collection
db.users.createIndex({ "username": 1 }, { unique: true })
```
