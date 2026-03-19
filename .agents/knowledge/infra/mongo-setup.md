# MongoDB Setup — OrderFlow

MongoDB running in Docker at `localhost:27018`. Database: `orderflow`.

---

## Connection Details

| Property   | Value                                                              |
|------------|--------------------------------------------------------------------|
| Host       | `localhost`                                                        |
| Port       | `27018` (container's 27017 mapped to host's 27018)                 |
| Database   | `orderflow`                                                        |
| Username   | `root`                                                             |
| Password   | `products`                                                         |
| AuthSource | `admin`                                                            |

Connection URI (used in all service `application.yml`):
```
mongodb://root:products@localhost:27018/orderflow?authSource=admin
```

The `authSource=admin` parameter is mandatory. The `root` user is stored in MongoDB's `admin`
database, not in `orderflow`. Without it, authentication fails.

---

## Collections

| Collection    | Entity    | Owned by               |
|---------------|-----------|------------------------|
| `orders`      | `Order`   | `order-service`        |
| `products`    | `Product` | `inventory-service`    |
| `users`       | `AppUser` | `order-security-server`|

Collections are created automatically by Spring Data MongoDB on first document insert.

---

## Spring Data MongoDB Configuration

Spring Boot autoconfigures `MongoClient` and `MongoTemplate` from the URI property:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://root:products@localhost:27018/orderflow?authSource=admin
```

No `@Configuration` class is needed for basic setup. The `inventory-service` has a `MongoDbConfig`
class — verify if it adds custom converters or if it's empty.

---

## MongoTemplate Usage

`MongoTemplate` is injected into `AbstractService` via `@Autowired`:

```java
@Autowired
private MongoTemplate mongoTemplate;
```

It is used in `AbstractService.list()`, `AbstractService.count()`, and any service method
that builds a dynamic query.

Direct usage in custom service methods:

```java
// Find orders by status, excluding soft-deleted
public List<Order> findByStatus(OrderStatus status) {
    Query query = new Query(
        Criteria.where("status").is(status)
                .and("metadata.deleted").ne(true)
    );
    query.with(Sort.by(Sort.Direction.DESC, "metadata.createdAt"));
    return mongoTemplate.find(query, Order.class);
}

// Count orders per customer
public long countByCustomer(String customerId) {
    Query query = new Query(
        Criteria.where("customerId").is(customerId)
                .and("metadata.deleted").ne(true)
    );
    return mongoTemplate.count(query, Order.class);
}

// Update a single field (partial update)
public void updateOrderStatus(String orderId, OrderStatus newStatus) {
    Query query = new Query(Criteria.where("_id").is(orderId));
    Update update = new Update()
        .set("status", newStatus)
        .set("metadata.updatedAt", LocalDateTime.now())
        .set("metadata.updatedBy", SecurityContextHelper.getCurrentUsername());
    mongoTemplate.updateFirst(query, update, Order.class);
}
```

---

## Soft Delete Queries

The base `AbstractService.buildQuery()` always adds:
```java
Criteria.where("metadata.deleted").ne(true)
```

This excludes documents where `metadata.deleted = true`. Documents where:
- `metadata.deleted = false` → included
- `metadata.deleted = null` → included (null is not equal to true)
- `metadata` field is absent → included
- `metadata.deleted = true` → excluded

When writing custom queries, always replicate this filter:
```java
Query query = new Query(Criteria.where("metadata.deleted").ne(true));
```

---

## Connecting with MongoDB Compass

GUI connection string:
```
mongodb://root:products@localhost:27018/orderflow?authSource=admin
```

Or use the connection form:
- Hostname: `localhost`
- Port: `27018`
- Authentication: Username/Password
- Username: `root`
- Password: `products`
- Auth DB: `admin`
- Database: `orderflow`

---

## Connecting via MongoDB CLI

```bash
docker exec -it products-db mongosh \
  "mongodb://root:products@localhost:27017/orderflow?authSource=admin"

# Inside the container's mongosh (port 27017 is the container-internal port):
use orderflow
db.orders.find({}).limit(5)
db.orders.countDocuments({"metadata.deleted": {$ne: true}})
db.products.find({"isActive": true})
db.users.find({}, {password: 0})  # Exclude password field
```

---

## Indexes (Recommended)

Create via mongosh or Spring's `@CompoundIndex`:

```javascript
// orders collection
db.orders.createIndex({"metadata.deleted": 1, "status": 1})
db.orders.createIndex({"customerId": 1, "metadata.deleted": 1})
db.orders.createIndex({"metadata.createdAt": -1})

// products collection
db.products.createIndex({"metadata.deleted": 1, "category": 1, "isActive": 1})
db.products.createIndex({"name": "text"})  // Full text search on name

// users collection
db.users.createIndex({"username": 1}, {"unique": true})
```

The `username` index must be unique to prevent duplicate users during concurrent seeding.

---

## Common Issues

### Authentication failed: bad auth
Cause: Missing or wrong `authSource=admin` in URI.
Fix: Use the full URI with `?authSource=admin`.

### Connection refused on port 27017
Cause: Connecting to wrong port. The host port is `27018`, not `27017`.
Fix: Use `localhost:27018` in all connection strings.

### MongoSocketReadException: Connection reset
Cause: MongoDB container is not running or crashed.
Fix: `docker ps | grep products-db` and restart if needed.

### "No matching documents" for a list query
Cause: Documents have `metadata.deleted: true` or the query criteria don't match.
Fix: Check `metadata.deleted` field in the document via Compass.
Also verify: the `AbstractService.buildQuery()` soft-delete filter is always applied.
