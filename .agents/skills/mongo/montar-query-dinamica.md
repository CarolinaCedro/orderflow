# Skill: Build Dynamic MongoDB Query

## Purpose

Build a `Query` with `Criteria` using `MongoTemplate` for filtering collections based on
dynamic request parameters. Always include the soft-delete filter.

## When to Use

- Implementing custom list endpoints with optional filter parameters.
- Overriding `AbstractService.list()` to add extra filters.
- Building queries with date ranges, multiple optional fields, or OR conditions.

## Prerequisites

- `MongoTemplate` available (injected into `AbstractService` or `@Autowired` in your service).
- Entity class defined in `order-model`.
- Target collection indexed on commonly filtered fields.

## Knowledge References

- `.agents/knowledge/spring/data-mongodb.md` — MongoTemplate patterns, Criteria builder
- `.agents/rules/coding-standards.md` — MongoTemplate for dynamic, MongoRepository for fixed

---

## Steps

### Step 1: Understand AbstractService.buildQuery()

The base class already handles request params:

```java
// In AbstractService<T>
private Query buildQuery(Map<String, String> params) {
    Query query = new Query(Criteria.where("metadata.deleted").ne(true));  // Always exclude soft-deleted
    params.entrySet().stream()
        .filter(e -> !RESERVED_PARAMS.contains(e.getKey()))  // Skip page, size, sort, returnEntity
        .forEach(e -> query.addCriteria(Criteria.where(e.getKey()).is(e.getValue())));
    return query;
}
```

This handles: `GET /orderflow/v1/order?status=COMPLETED&customerId=abc`

### Step 2: Simple Dynamic Query — Extend Base Pattern

For a custom endpoint with typed parameters:

```java
// In OrderServiceImpl
public List<Order> findByStatusAndDateRange(OrderStatus status,
                                             LocalDateTime from, LocalDateTime to) {
    Query query = new Query();
    Criteria criteria = Criteria.where("metadata.deleted").ne(true);  // Always first

    if (status != null) {
        criteria = criteria.and("status").is(status);
    }
    if (from != null && to != null) {
        criteria = criteria.and("metadata.createdAt").gte(from).lte(to);
    } else if (from != null) {
        criteria = criteria.and("metadata.createdAt").gte(from);
    }

    query.addCriteria(criteria);
    query.with(Sort.by(Sort.Direction.DESC, "metadata.createdAt"));

    log.debug("Executing query: {}", query);
    return mongoTemplate.find(query, Order.class);
}
```

### Step 3: Query with OR Conditions

```java
// Find orders that are either PENDING or APPROVED
public List<Order> findPendingOrApproved() {
    Query query = new Query();
    Criteria criteria = new Criteria().andOperator(
        Criteria.where("metadata.deleted").ne(true),
        new Criteria().orOperator(
            Criteria.where("status").is(OrderStatus.PENDING_APPROVAL),
            Criteria.where("status").is(OrderStatus.APPROVED)
        )
    );
    query.addCriteria(criteria);
    return mongoTemplate.find(query, Order.class);
}
```

### Step 4: Query with $in (Batch Lookup)

```java
// Find all products by a list of IDs (avoids N+1)
public List<Product> findProductsByIds(List<String> productIds) {
    Query query = new Query(
        Criteria.where("_id").in(productIds)
                .and("metadata.deleted").ne(true)
                .and("isActive").is(true)
    );
    return mongoTemplate.find(query, Product.class);
}
```

### Step 5: Projection — Fetch Only Required Fields

```java
// Fetch only id and status for a status check (avoids loading items, metadata, etc.)
public List<Order> findOrderStatusByCustomer(String customerId) {
    Query query = new Query(
        Criteria.where("customerId").is(customerId)
                .and("metadata.deleted").ne(true)
    );
    query.fields()
        .include("id")
        .include("status")
        .include("approvalStatus");

    return mongoTemplate.find(query, Order.class);
}
```

Note: fields not included in the projection will be `null` in the returned objects.
Do not pass projected objects to logic that assumes full documents.

### Step 6: Partial Update (Update Only Specific Fields)

```java
public void updateOrderStatus(String orderId, OrderStatus newStatus) {
    Query query = new Query(Criteria.where("_id").is(orderId));
    Update update = new Update()
        .set("status", newStatus)
        .set("metadata.updatedAt", LocalDateTime.now())
        .set("metadata.updatedBy", SecurityContextHelper.getCurrentUsername());
    mongoTemplate.updateFirst(query, update, Order.class);
    log.info("Order {} status updated to {}", orderId, newStatus);
}
```

### Step 7: Pagination with MongoTemplate

```java
public List<Order> findPagedByCustomer(String customerId, int page, int size) {
    Query query = new Query(
        Criteria.where("customerId").is(customerId)
                .and("metadata.deleted").ne(true)
    );
    query.with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "metadata.createdAt")));
    return mongoTemplate.find(query, Order.class);
}

public long countByCustomer(String customerId) {
    Query query = new Query(
        Criteria.where("customerId").is(customerId)
                .and("metadata.deleted").ne(true)
    );
    return mongoTemplate.count(query, Order.class);
}
```

---

## Validation Checklist

- [ ] Every query includes `Criteria.where("metadata.deleted").ne(true)`
- [ ] `MongoTemplate` injected (inherited from `AbstractService` or `@Autowired`)
- [ ] Field paths match exact entity field names (camelCase, dot notation for nested)
- [ ] Enum values passed as enum (not String) to `is()` — e.g., `is(OrderStatus.COMPLETED)`
- [ ] For request-param driven queries, `RESERVED_PARAMS` excluded
- [ ] Sort direction specified when result order matters

## Common Mistakes

- Forgetting `metadata.deleted != true` filter — returns soft-deleted records.
- Using `"status"` as a string in `is("COMPLETED")` vs `is(OrderStatus.COMPLETED)` — both work
  because MongoDB stores enums as strings, but be consistent.
- `addCriteria()` called multiple times on the same field — throws `InvalidMongoDbApiUsageException`.
  Use `andOperator()` for multiple conditions on the same document.
- `mongoTemplate.findOne()` returns `null` (not `Optional`) — always null-check the result.
- Not logging queries in debug mode — hard to diagnose issues without query visibility.
