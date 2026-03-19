# Docker Setup — OrderFlow

Docker Compose files live in `@docker-compose/`. Two separate compose files: MongoDB and Kafka.

---

## MongoDB Compose

File: `@docker-compose/mongo-db/docker-compose.yml`

```yaml
version: '3.8'

services:
  mongodb:
    image: mongo:latest
    container_name: products-db
    ports:
      - "27018:27017"    # External:Internal — host port 27018 maps to container's 27017
    volumes:
      - mongo-data:/data/db
    environment:
      MONGO_INITDB_ROOT_USERNAME: root
      MONGO_INITDB_ROOT_PASSWORD: products
    networks:
      - mongo-network

volumes:
  mongo-data:

networks:
  mongo-network:
    driver: bridge
```

Key details:
- Container name: `products-db` (legacy name — the database is `orderflow`).
- Port: `27018:27017` — external host uses port `27018` to avoid conflict with any locally
  installed MongoDB (which typically runs on `27017`).
- Credentials: `root` / `products` with `authSource=admin`.
- Database: `orderflow` (created automatically on first connection).
- Collections auto-created: `orders`, `products`, `users`.

---

## Kafka Compose

File: `@docker-compose/kafka/docker-compose.yml`

```yaml
version: '3'
services:
  zookeeper:
    image: zookeeper:3.4.9
    hostname: zookeeper
    ports:
      - "2181:2181"
    environment:
      ZOO_MY_ID: 1
      ZOO_PORT: 2181
      ZOO_SERVERS: server.1=zookeeper:2888:3888
    volumes:
      - ./data/zookeeper/data:/data
      - ./data/zookeeper/datalog:/datalog

  kafka1:
    image: confluentinc/cp-kafka:5.3.0
    hostname: kafka1
    ports:
      - "9091:9091"      # External listener on port 9091
    environment:
      KAFKA_ADVERTISED_LISTENERS: LISTENER_DOCKER_INTERNAL://kafka1:19091,LISTENER_DOCKER_EXTERNAL://${DOCKER_HOST_IP:-127.0.0.1}:9091
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: LISTENER_DOCKER_INTERNAL:PLAINTEXT,LISTENER_DOCKER_EXTERNAL:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: LISTENER_DOCKER_INTERNAL
      KAFKA_ZOOKEEPER_CONNECT: "zookeeper:2181"
      KAFKA_BROKER_ID: 1
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_CREATE_TOPICS: "vendas-topico:1:1,payment-processed:1:1"
    volumes:
      - ./data/kafka1/data:/var/lib/kafka/data
    depends_on:
      - zookeeper

  kafdrop:
    image: obsidiandynamics/kafdrop
    restart: "no"
    ports:
      - "9000:9000"
    environment:
      KAFKA_BROKERCONNECT: "kafka1:19091"
    depends_on:
      - kafka1
```

Key details:
- Two listeners: `LISTENER_DOCKER_INTERNAL` (for inter-container communication on port 19091)
  and `LISTENER_DOCKER_EXTERNAL` (for host access on port 9091).
- Java services on the host use `localhost:9091` (LISTENER_DOCKER_EXTERNAL).
- Topics created automatically: `vendas-topico:1:1` (1 partition, 1 replica),
  `payment-processed:1:1`.
- Kafdrop UI at `http://localhost:9000` — browse topics, view messages, check consumer groups.

---

## Startup Order

Start containers before starting Java services:

```bash
# Terminal 1 — MongoDB
cd @docker-compose/mongo-db
docker compose up -d

# Terminal 2 — Kafka
cd @docker-compose/kafka
docker compose up -d

# Wait ~15 seconds for Kafka to initialize, then start Java services
```

Check readiness:
```bash
# MongoDB ready
docker logs products-db | grep "Waiting for connections"

# Kafka ready
docker logs kafka1 | grep "started (kafka.server.KafkaServer)"

# Kafdrop ready
curl -s http://localhost:9000 | grep -q "Kafdrop"
```

---

## Port Conflict Resolution

Port `27017` is commonly used by locally installed MongoDB. OrderFlow uses `27018` to avoid conflict.

If port `9091` is in use:
```bash
lsof -i :9091
# If occupied, stop the conflicting process or change the compose port mapping
```

If port `27018` is in use:
```bash
lsof -i :27018
# Stop conflicting process (usually a stale docker container)
docker ps | grep mongo
docker stop <container_id>
```

---

## Data Directory Gitignore

The `@docker-compose/kafka/data/` directory is created by Docker and contains Kafka and Zookeeper
data files. It should be in `.gitignore`:

```
@docker-compose/kafka/data/
@docker-compose/mongo-db/data/
```

Never commit data directories to git.

---

## Resetting State

### Reset Kafka (clear all messages and consumer offsets)
```bash
cd @docker-compose/kafka
docker compose down
rm -rf data/
docker compose up -d
```

### Reset MongoDB (clear all data)
```bash
cd @docker-compose/mongo-db
docker compose down -v   # -v removes named volumes
docker compose up -d
```

After resetting MongoDB, `DataInitializer` in `order-security-server` will re-seed the users.

---

## Common Issues

### Kafka not reachable at localhost:9091
1. Verify containers are running: `docker ps`
2. Verify the port mapping: `docker port kafka1`
3. Check `DOCKER_HOST_IP` env var — it defaults to `127.0.0.1` (correct for local dev)

### MongoDB authentication failed
Credentials: username=`root`, password=`products`, authSource=`admin`
URI: `mongodb://root:products@localhost:27018/orderflow?authSource=admin`

The `?authSource=admin` part is required — MongoDB stores root user in the `admin` database.

### Kafdrop showing no topics
Topics are created at Kafka startup via `KAFKA_CREATE_TOPICS`. If they don't appear:
```bash
docker logs kafka1 | grep "vendas-topico"
# If empty, recreate the containers
docker compose down && docker compose up -d
```
