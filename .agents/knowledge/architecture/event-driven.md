# Event-Driven Architecture — OrderFlow

Full event flow, payload structures, status transitions, and extension patterns.

---

## Complete Event Flow

```
[Client]
    │
    │  POST /order-service/orderflow/v1/order
    │  Authorization: Bearer <JWT>
    │  Body: { customerId, customerName, items, totalAmount }
    ▼
[order-service:8081]
    │  1. Validates Order (Jakarta Validation)
    │  2. Saves to MongoDB orders collection
    │  3. Publishes to Kafka: vendas-topico (key=orderId, value=orderId)
    │  4. Returns HTTP 200 with saved Order
    ▼
[Kafka: vendas-topico]
    │
    ├──────────────────────────────────────────────────►
    │                                        [payment-service:8085]
    │                                            │  1. Receives orderId
    │                                            │  2. log.info("Order received: {}", orderId)
    │                                            │  3. simulatePayment() → "PAYMENT_SUCCESS"
    │                                            │  4. Builds JSON payload
    │                                            │  5. Publishes to payment-processed
    │                                            │  6. log.info("Payment processed: {}", status)
    │                                            ▼
    │                                    [Kafka: payment-processed]
    │                                            │
    │                           ┌────────────────┴────────────────┐
    │                           ▼                                 ▼
    │               [order-service:8081]              [notification-service:8083]
    │                   │  1. Receives JSON            │  1. Receives JSON
    │                   │  2. Parses orderId+status    │  2. Parses orderId+status
    │                   │  3. Updates Order in MongoDB │  3. Calls sendConfirmationEmail()
    │                   │     status=COMPLETED         │     or sendFailureAlert()
    │                   │     approvalStatus=APPROVED  │  4. Logs [EMAIL] notification
    │                   │     approvedBy=payment-service
    │                   │     approvalDate=now()
    ▼
[MongoDB: orders collection]
    Final state: { status: "COMPLETED", approvalStatus: "APPROVED", approvedBy: "payment-service" }
```

---

## Payload Structures

### vendas-topico

```
Key:   "64c9f8a1b5e3f20012345678"      (MongoDB ObjectId as string)
Value: "64c9f8a1b5e3f20012345678"      (same — orderId only)
```

The key and value are identical. Kafka key is used for partition assignment (same orderId
always goes to same partition, preserving ordering for a single order).

### payment-processed

```
Key:   "64c9f8a1b5e3f20012345678"      (orderId)
Value: {
    "orderId":     "64c9f8a1b5e3f20012345678",
    "status":      "PAYMENT_SUCCESS",    or "PAYMENT_FAILURE"
    "processedAt": "2025-03-19T10:30:00.000Z"
}
```

---

## Status Transitions via Events

`Order.status` field transitions:

```
Initial (on save): DRAFT or PENDING_APPROVAL
                          │
                          │  (Kafka: vendas-topico sent)
                          ▼
              [payment-service processing]
                          │
          ┌───────────────┴───────────────┐
          │ PAYMENT_SUCCESS               │ PAYMENT_FAILURE (or any other status)
          ▼                               ▼
      COMPLETED                       CANCELLED
```

`Order.approvalStatus` transitions:

```
Initial: null or NOT_REQUIRED
          │
          │  on PAYMENT_SUCCESS
          ▼
       APPROVED
          │  on PAYMENT_FAILURE
          ▼
       REJECTED
```

These transitions happen in `OrderPaymentListener.onPaymentProcessed()`:

```java
if ("PAYMENT_SUCCESS".equals(status)) {
    order.setStatus(OrderStatus.COMPLETED);
    order.setApprovalStatus(ApprovalStatus.APPROVED);
    order.setApprovedBy("payment-service");
    order.setApprovalDate(LocalDateTime.now());
} else {
    order.setStatus(OrderStatus.CANCELLED);
    order.setApprovalStatus(ApprovalStatus.REJECTED);
}
orderRepository.save(order);
```

---

## Idempotency Considerations

### Current state (no idempotency)

If `payment-service` processes the same orderId twice (e.g., due to consumer restart or
duplicate message), it will:
1. Publish two `payment-processed` events with the same orderId.
2. `order-service` will update the order twice (idempotent for same status).
3. `notification-service` will send two notifications (NOT idempotent).

### Implementing idempotency for consumers

For `order-service` (`OrderPaymentListener`):
```java
// Check if order is already in terminal state before updating
orderRepository.findById(orderId).ifPresentOrElse(order -> {
    if (order.getStatus() == OrderStatus.COMPLETED
            || order.getStatus() == OrderStatus.CANCELLED) {
        log.warn("Order {} already in terminal state {}. Skipping update.", orderId, order.getStatus());
        return;
    }
    // proceed with update...
}, () -> log.error("Order {} not found", orderId));
```

For `notification-service`: store a set of processed orderIds and skip duplicates.

---

## Extending the Event Flow

### Adding a new consumer to payment-processed

1. Create a new service or add a new `@KafkaListener` in an existing service.
2. Use a new group ID (e.g., `erp-sync-group`).
3. Parse the payload and perform your action.
4. No changes to `payment-service` (the producer) are needed.

Example: ERP sync service
```java
@KafkaListener(topics = "payment-processed", groupId = "erp-sync-group")
public void syncToErp(String payload) {
    try {
        JsonNode node = objectMapper.readTree(payload);
        String orderId = node.path("orderId").asText();
        if ("PAYMENT_SUCCESS".equals(node.path("status").asText())) {
            erpClient.createSalesOrder(orderId);
            log.info("ERP sales order created for order {}", orderId);
        }
    } catch (Exception e) {
        log.error("Failed to sync order to ERP. Payload: {}", payload, e);
    }
}
```

### Adding a new event (new topic)

1. Define the topic name as a constant in the producer service.
2. Create the topic in Docker Compose `KAFKA_CREATE_TOPICS` (or via Kafka admin client).
3. Publish from the producer using `KafkaTemplate`.
4. Create consumers with appropriate group IDs.

Example: adding `inventory-reserved` topic
```java
// In inventory-service:
private static final String INVENTORY_RESERVED_TOPIC = "inventory-reserved";

public void reserveStock(String orderId, List<OrderItem> items) {
    // reduce stock...
    String payload = buildInventoryReservedPayload(orderId, items);
    kafkaTemplate.send(INVENTORY_RESERVED_TOPIC, orderId, payload);
    log.info("Inventory reserved for order {}, published to {}", orderId, INVENTORY_RESERVED_TOPIC);
}
```

---

## Correlation IDs

`Metadata.correlationId` exists for distributed tracing. Currently not populated automatically.

Future implementation:
```java
// In GatewaySecurityConfig or a GlobalFilter:
String correlationId = exchange.getRequest().getHeaders()
    .getFirst("X-Correlation-ID");
if (correlationId == null) {
    correlationId = UUID.randomUUID().toString();
}
// Add to request headers forwarded to services
```

```java
// In OrderServiceImpl.save():
Metadata meta = order.getMetadata();
if (meta == null) meta = new Metadata();
meta.setCorrelationId(request.getHeader("X-Correlation-ID"));
```

Include correlation ID in Kafka payloads to trace an order across all services.
