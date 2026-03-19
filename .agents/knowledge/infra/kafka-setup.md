# Kafka Setup — OrderFlow

Kafka 5.3.0 (Confluent Platform) with Zookeeper. Bootstrap server: `localhost:9091`.

---

## Configuration Reference

| Property                  | Value                    |
|---------------------------|--------------------------|
| Bootstrap servers         | `localhost:9091`         |
| Zookeeper                 | `localhost:2181`         |
| Kafdrop UI                | `http://localhost:9000`  |
| Internal broker listener  | `kafka1:19091`           |
| External host listener    | `localhost:9091`         |
| Topics                    | `vendas-topico`, `payment-processed` |

---

## Topics

| Topic               | Partitions | Replicas | Retention    |
|---------------------|------------|----------|--------------|
| `vendas-topico`     | 1          | 1        | Default 7d   |
| `payment-processed` | 1          | 1        | Default 7d   |

Topics are auto-created by Docker Compose:
```yaml
KAFKA_CREATE_TOPICS: "vendas-topico:1:1,payment-processed:1:1"
```

Format: `topic-name:partitions:replicas`

---

## Consumer Groups

| Group ID              | Service               | Topic consumed        |
|-----------------------|-----------------------|-----------------------|
| `payment-group`       | `payment-service`     | `vendas-topico`       |
| `order-group`         | `order-service`       | `payment-processed`   |
| `notification-group`  | `notification-service`| `payment-processed`   |

Verify groups in Kafdrop: `http://localhost:9000` → Consumer Groups tab.

---

## application.yml Consumer Config

Producer (order-service, payment-service):
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9091
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

Consumer (payment-service — consumes string orderId):
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9091
    consumer:
      group-id: payment-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

Consumer (order-service — consumes JSON string):
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9091
    consumer:
      group-id: order-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

`auto-offset-reset: earliest` means: if this consumer group has no committed offset (first run),
start reading from the beginning of the topic. Existing messages in the topic will be processed.

---

## Log Flooding Prevention

The Kafka client library is very verbose by default. Always suppress it:

```yaml
logging:
  level:
    org.apache.kafka: WARN
    kafka: WARN
    org.apache.zookeeper: WARN
```

Without this, the console fills with coordination messages, heartbeat logs, and metadata
refresh logs every few seconds, making application logs unreadable.

In `inventory-service/application.yml`, this is already set. Apply to all services.

---

## Kafdrop — Kafka UI

Kafdrop runs at `http://localhost:9000`.

Features available:
- **Topics**: List all topics, view partition details, browse messages.
- **Consumer Groups**: View group offsets, lag, consumer assignments.
- **Messages**: Read messages from any topic/partition/offset.

### Browsing a message
1. Open `http://localhost:9000`.
2. Click "Topics".
3. Click `vendas-topico`.
4. Click "View Messages".
5. Select partition 0, set offset to 0, set count to 10.
6. Click "View Messages".

### Checking consumer lag
1. Open `http://localhost:9000`.
2. Click "Consumer Groups".
3. Find `payment-group`.
4. View lag — `0` means all messages have been consumed.

---

## Creating Topics Manually (If Auto-Creation Fails)

Via Kafka CLI inside the container:
```bash
docker exec -it kafka1 kafka-topics \
  --create \
  --zookeeper zookeeper:2181 \
  --replication-factor 1 \
  --partitions 1 \
  --topic vendas-topico

docker exec -it kafka1 kafka-topics \
  --create \
  --zookeeper zookeeper:2181 \
  --replication-factor 1 \
  --partitions 1 \
  --topic payment-processed
```

List topics:
```bash
docker exec -it kafka1 kafka-topics --list --zookeeper zookeeper:2181
```

---

## Sending a Test Message Manually

```bash
docker exec -it kafka1 kafka-console-producer \
  --broker-list kafka1:19091 \
  --topic vendas-topico
# Type: test-order-id-123
# Press Ctrl+C to exit
```

This triggers `payment-service` to process `test-order-id-123` (which won't exist in MongoDB,
but the consumer will log an attempt).

---

## Reading Messages Manually

```bash
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19091 \
  --topic payment-processed \
  --from-beginning \
  --max-messages 10
```

---

## Common Issues

### "Connection to node -1 could not be established"
Cause: Wrong `bootstrap-servers` port. Verify it's `localhost:9091`, not `9092`.

### Consumer not receiving messages
1. Check group ID matches the `@KafkaListener` annotation.
2. Check the topic name is exactly `vendas-topico` (case-sensitive).
3. Check `auto-offset-reset: earliest` if the consumer started after messages were sent.
4. Verify topic exists in Kafdrop.

### "org.apache.kafka.common.errors.TimeoutException"
Cause: Kafka is not reachable. Verify Docker container is running:
```bash
docker ps | grep kafka1
```

### KafkaMonitorController / KafkaEventStore
If the project references a `KafkaMonitorController`, it's a custom endpoint that reads events
from a MongoDB collection where consumed Kafka messages are stored for auditing.
Check `order-service` for any `@Document(collection = "kafka_events")` entity.
