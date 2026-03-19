# Skill: Implement Soft Delete

## Purpose

Implement soft delete for any entity using `Metadata.deleted = true` instead of physically
removing the document from MongoDB. This is mandatory for all entities in OrderFlow.

## When to Use

- Overriding `deleteById()` in any service extending `AbstractService<T>`.
- Adding soft delete to a service that currently uses hard delete.
- Implementing admin restore functionality (undelete).

## Prerequisites

- Entity has a `Metadata metadata` field.
- `Metadata` class has `deleted`, `deletedAt`, `deletedBy` fields.
- `SecurityContextHelper` available from `order-utils`.

## Knowledge References

- `.agents/knowledge/spring/data-mongodb.md` — soft delete pattern, metadata structure
- `.agents/rules/architecture-guidelines.md` — soft delete mandate
- `.agents/knowledge/orderflow/pedidos.md` — Metadata lifecycle

---

## Steps

### Step 1: Verify Metadata Class Has Required Fields

`org.cedro.ordermodel.model.Metadata` must have:
```java
private Boolean deleted = false;
private String deletedBy;
private LocalDateTime deletedAt;
```

These are already present in the current `Metadata` implementation.

### Step 2: Override deleteById in Service Implementation

Override `deleteById` in the service (not the controller — the controller just calls `super.deleteById(id)`):

```java
@Override
public void deleteById(String id) {
    orderRepository.findById(id).ifPresentOrElse(order -> {
        Metadata meta = order.getMetadata();
        if (meta == null) {
            meta = new Metadata();
            order.setMetadata(meta);
        }
        meta.setDeleted(true);
        meta.setDeletedAt(LocalDateTime.now());
        meta.setDeletedBy(SecurityContextHelper.getCurrentUsername());

        orderRepository.save(order);
        log.info("Order {} soft-deleted by {}", id, meta.getDeletedBy());

    }, () -> {
        throw new ResourceNotFoundException(new ErrorView(
            LocalDateTime.now(),
            "Order not found",
            HttpStatus.NOT_FOUND,
            "Not Found",
            id,
            Order.class
        ));
    });
}
```

### Step 3: Verify list() and count() Exclude Soft-Deleted Records

The base `AbstractService.buildQuery()` already does this:
```java
Query query = new Query(Criteria.where("metadata.deleted").ne(true));
```

This matches documents where `deleted` is `false`, `null`, or the field doesn't exist.
Only `deleted = true` is excluded.

Verify your custom queries also include this filter (see `montar-query-dinamica.md`).

### Step 4: Handle findById for Soft-Deleted Records

By default, `AbstractService.findById()` uses `getRepository().findById(id)` which does NOT
filter soft-deleted records. If a client tries to access a soft-deleted order by ID, they
will see it.

To exclude soft-deleted records from `findById()`:

```java
@Override
public ResponseEntity<Order> findById(String id) {
    Query query = new Query(
        Criteria.where("_id").is(id)
                .and("metadata.deleted").ne(true)
    );
    return Optional.ofNullable(mongoTemplate.findOne(query, Order.class))
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new ResourceNotFoundException(new ErrorView(
            LocalDateTime.now(), "Order not found",
            HttpStatus.NOT_FOUND, "Not Found", id, Order.class
        )));
}
```

### Step 5: Implement Restore (Undelete) — Admin Only

```java
public void restoreById(String id) {
    orderRepository.findById(id).ifPresentOrElse(order -> {
        Metadata meta = order.getMetadata();
        if (meta == null || !Boolean.TRUE.equals(meta.getDeleted())) {
            log.warn("Order {} is not deleted — nothing to restore", id);
            return;
        }
        meta.setDeleted(false);
        meta.setDeletedAt(null);
        meta.setDeletedBy(null);
        meta.setUpdatedAt(LocalDateTime.now());
        meta.setUpdatedBy(SecurityContextHelper.getCurrentUsername());
        order.setMetadata(meta);
        orderRepository.save(order);
        log.info("Order {} restored by {}", id, meta.getUpdatedBy());
    }, () -> {
        throw new ResourceNotFoundException(new ErrorView(
            LocalDateTime.now(), "Order not found",
            HttpStatus.NOT_FOUND, "Not Found", id, Order.class
        ));
    });
}
```

Add to controller with ADMIN-only access:
```java
@DeleteMapping("/{id}/restore")
@PreAuthorize("hasAuthority('ADMIN')")
public ResponseEntity<Void> restore(@PathVariable String id) {
    orderService.restoreById(id);
    return ResponseEntity.ok().build();
}
```

### Step 6: Admin Query That Includes Soft-Deleted Records

For admin views that need to see everything including deleted:

```java
public List<Order> findAllIncludingDeleted(String customerId) {
    // Note: NO metadata.deleted filter
    Query query = new Query(Criteria.where("customerId").is(customerId));
    query.with(Sort.by(Sort.Direction.DESC, "metadata.createdAt"));
    return mongoTemplate.find(query, Order.class);
}
```

---

## Validation Checklist

- [ ] `deleteById()` overridden in service implementation (not just in controller)
- [ ] Soft delete sets `metadata.deleted = true`
- [ ] `metadata.deletedAt` set to `LocalDateTime.now()`
- [ ] `metadata.deletedBy` set from `SecurityContextHelper.getCurrentUsername()`
- [ ] `orderRepository.save(order)` called after modifying metadata
- [ ] `ResourceNotFoundException` thrown if entity not found
- [ ] Log statement confirms the soft delete with entity ID and actor
- [ ] Custom queries include `Criteria.where("metadata.deleted").ne(true)`

## Common Mistakes

- Calling `orderRepository.deleteById(id)` — hard deletes the document permanently.
- Not initializing `Metadata` before setting fields — `NullPointerException` if `metadata` is null.
- Forgetting `ne(true)` vs `is(false)` — `ne(true)` also matches null (safer).
  Using `is(false)` would miss records where `metadata.deleted` was never set.
- Not logging who performed the delete — compliance and audit trail require this.
- Making restore endpoint available to non-admin roles.
