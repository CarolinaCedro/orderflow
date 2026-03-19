# OrderFlow — Diagramas de Fluxo e Mensageria

> Documentação visual completa da arquitetura de mensageria — **100% Kafka**, sem RabbitMQ.

---

## Índice

1. [Visão Geral da Arquitetura](#1-visão-geral-da-arquitetura)
2. [Ciclo de Vida do Pedido (State Machine)](#2-ciclo-de-vida-do-pedido)
3. [Fluxo Completo — Sequência de Ponta a Ponta](#3-fluxo-completo--sequência-de-ponta-a-ponta)
4. [Tópicos Kafka](#4-tópicos-kafka)
5. [Fluxo Kafka em Detalhe](#5-fluxo-kafka-em-detalhe)
6. [Eventos de Domínio Internos (Spring Events)](#6-eventos-de-domínio-internos-spring-events)
7. [Hierarquia de Classes — Abstrações CRUD](#7-hierarquia-de-classes--abstrações-crud)
8. [Dependências entre Módulos](#8-dependências-entre-módulos)
9. [Fluxo de Dados no MongoDB](#9-fluxo-de-dados-no-mongodb)

---

## 1. Visão Geral da Arquitetura

```mermaid
graph TB
    CLIENT(["👤 Cliente / Frontend"])

    subgraph INFRA["🏗️ Infraestrutura"]
        CONFIG["Config Server\n:8888"]
        EUREKA["Eureka Server\n:8761\nService Registry"]
    end

    subgraph GATEWAY_ZONE["🔀 API Gateway :8080"]
        GATEWAY["Spring Cloud Gateway\nRoteamento automático via Eureka"]
    end

    subgraph SERVICES["⚙️ Microserviços de Negócio"]
        ORDER["Order Service\n:8081\nCRUD de Pedidos"]
        PAYMENT["Payment Service\n:8085\nProcessamento de Pagamento"]
        INVENTORY["Inventory Service\n:8089\nEstoque e Produtos"]
        NOTIFICATION["Notification Service\n:8083\nNotificações"]
        SECURITY["Order Security Server\n(WIP)"]
    end

    subgraph KAFKA_CLUSTER["⚡ Apache Kafka :9091"]
        T1["Tópico: vendas-topico\n(Order → Payment)"]
        T2["Tópico: payment-processed\n(Payment → Notification)"]
    end

    subgraph DB["🗄️ Persistência"]
        MONGO_ORDERS[("MongoDB\norders\n:27018")]
        MONGO_PRODUCTS[("MongoDB\nproducts\n:27018")]
    end

    subgraph EXTERNAL["🌐 Externo"]
        VIACEP["ViaCEP API"]
        ERP["ERP Externo"]
    end

    CLIENT -->|HTTP| GATEWAY
    GATEWAY -->|Service Discovery| EUREKA
    GATEWAY --> ORDER
    GATEWAY --> INVENTORY

    CONFIG -.->|Configuração| ORDER & PAYMENT & INVENTORY & NOTIFICATION
    EUREKA -.->|Registrado| ORDER & PAYMENT & INVENTORY & NOTIFICATION

    ORDER -->|Persiste| MONGO_ORDERS
    ORDER -->|"produce: orderId"| T1
    ORDER -->|Feign HTTP| VIACEP
    ORDER -.->|Integração| ERP

    T1 -->|"consume\ngroup: payment-group"| PAYMENT
    PAYMENT -->|"produce: orderId + status"| T2
    T2 -->|"consume\ngroup: notification-group"| NOTIFICATION

    INVENTORY -->|Persiste| MONGO_PRODUCTS

    style T1 fill:#e3f2fd,stroke:#1565C0,color:#000
    style T2 fill:#e3f2fd,stroke:#1565C0,color:#000
    style KAFKA_CLUSTER fill:#bbdefb,stroke:#1565C0,color:#000
    style MONGO_ORDERS fill:#e8f5e9,stroke:#4CAF50,color:#000
    style MONGO_PRODUCTS fill:#e8f5e9,stroke:#4CAF50,color:#000
    style GATEWAY fill:#f3e5f5,stroke:#9C27B0,color:#000
    style EUREKA fill:#fce4ec,stroke:#E91E63,color:#000
    style CONFIG fill:#fce4ec,stroke:#E91E63,color:#000
```

---

## 2. Ciclo de Vida do Pedido

```mermaid
stateDiagram-v2
    direction LR

    [*] --> DRAFT : POST /orderflow/v1/order

    DRAFT --> PENDING_APPROVAL : Cliente submete para aprovação

    PENDING_APPROVAL --> APPROVED : Aprovador aceita\n(approvedBy + approvalDate)
    PENDING_APPROVAL --> REJECTED : Aprovador rejeita
    PENDING_APPROVAL --> REVISION_REQUESTED : Solicita revisão

    REVISION_REQUESTED --> PENDING_APPROVAL : Cliente reenvia

    APPROVED --> SENT_TO_ERP : Kafka publica em vendas-topico\nPayment Service processa
    APPROVED --> CANCELLED : Cancelado após aprovação

    SENT_TO_ERP --> COMPLETED : ERP confirma\nNotification Service notifica cliente
    SENT_TO_ERP --> CANCELLED : Falha no pagamento

    REJECTED --> [*]
    COMPLETED --> [*]
    CANCELLED --> [*]

    note right of APPROVED
        Order Service publica orderId
        no tópico: vendas-topico
    end note

    note right of SENT_TO_ERP
        Payment Service publica resultado
        no tópico: payment-processed
    end note

    note right of COMPLETED
        Notification Service consome
        payment-processed e notifica cliente
    end note
```

---

## 3. Fluxo Completo — Sequência de Ponta a Ponta

```mermaid
sequenceDiagram
    autonumber

    actor Cliente
    participant GW as API Gateway<br/>:8080
    participant OS as Order Service<br/>:8081
    participant MongoDB as MongoDB<br/>:27018
    participant SE as Spring<br/>ApplicationEvent
    participant T1 as Kafka<br/>vendas-topico
    participant PS as Payment Service<br/>:8085
    participant T2 as Kafka<br/>payment-processed
    participant NS as Notification Service<br/>:8083
    participant IS as Inventory Service<br/>:8089

    rect rgb(232, 244, 253)
        note over Cliente, OS: Fase 1 — Criação do Pedido
        Cliente->>+GW: POST /order-service/orderflow/v1/order
        GW->>+OS: Roteia via Eureka
        OS->>+MongoDB: save(order) — status: DRAFT
        MongoDB-->>-OS: Order { id: "abc123" }
        OS->>SE: publish(OrderCreatedEvent("abc123"))
        SE-->>OS: log.info("Order created: abc123")
        OS-->>-GW: 200 OK → Order JSON
        GW-->>-Cliente: 200 OK
    end

    rect rgb(255, 243, 224)
        note over Cliente, OS: Fase 2 — Aprovação do Pedido
        Cliente->>+GW: PUT /order/abc123 { approvalStatus: APPROVED }
        GW->>+OS: Roteia
        OS->>+MongoDB: update → status: APPROVED, approvedBy: "gerente"
        MongoDB-->>-OS: OK
        OS->>+T1: kafkaTemplate.send("vendas-topico", "abc123")
        Note right of T1: offset: 42<br/>partition: 0
        T1-->>-OS: ACK
        OS-->>-GW: 200 OK
        GW-->>-Cliente: 200 OK
    end

    rect rgb(232, 245, 233)
        note over T1, T2: Fase 3 — Payment Service consome vendas-topico e publica em payment-processed
        T1->>+PS: @KafkaListener — group: payment-group<br/>orderId: "abc123"
        PS->>PS: processarVenda("abc123")<br/>processa pagamento
        PS->>+T2: kafkaTemplate.send("payment-processed", "abc123", "PAYMENT_SUCCESS")
        Note right of T2: offset: 15<br/>partition: 0
        T2-->>-PS: ACK
        PS->>T1: commitOffset(partition: 0, offset: 43)
        PS-->>-T1: offset committed
    end

    rect rgb(243, 229, 245)
        note over T2, IS: Fase 4 — Notificação e Atualização de Estoque
        T2->>+NS: @KafkaListener — group: notification-group<br/>"PAYMENT_SUCCESS"
        NS->>NS: log.info("Payment status received: PAYMENT_SUCCESS")
        NS->>Cliente: Envia notificação (email/SMS — a implementar)
        NS->>T2: commitOffset
        NS-->>-T2: OK

        IS->>+MongoDB: update product.stockQuantity -= qty
        MongoDB-->>-IS: Estoque atualizado
    end
```

---

## 4. Tópicos Kafka

| Tópico | Producer | Consumer (group) | Payload | Finalidade |
|---|---|---|---|---|
| `vendas-topico` | Order Service | Payment Service (`payment-group`) | `orderId` (String) | Dispara processamento de pagamento ao aprovar pedido |
| `payment-processed` | Payment Service | Notification Service (`notification-group`) | `orderId:status` (String) | Notifica resultado do pagamento |

### Por que dois tópicos e não um?

```mermaid
graph LR
    subgraph UM_TOPICO["❌ Um único tópico — problema"]
        A1["vendas-topico"] -->|consome| B1["Payment Service"]
        A1 -->|consome| C1["Notification Service"]
        note1["Notification Service receberia o evento\nANTES do pagamento ser processado"]
    end

    subgraph DOIS_TOPICOS["✅ Dois tópicos — correto"]
        A2["vendas-topico"] -->|"1. consome\ngroup: payment-group"| B2["Payment Service"]
        B2 -->|"2. publica resultado"| D["payment-processed"]
        D -->|"3. consome\ngroup: notification-group"| C2["Notification Service"]
        note2["Notification Service só é acionado\nAPÓS o pagamento ser processado"]
    end

    style UM_TOPICO fill:#ffebee,stroke:#c62828,color:#000
    style DOIS_TOPICOS fill:#e8f5e9,stroke:#2e7d32,color:#000
```

---

## 5. Fluxo Kafka em Detalhe

### 5.1 Arquitetura interna do cluster

```mermaid
graph TB
    subgraph DOCKER["🐳 Docker Compose — kafka/docker-compose.yml"]
        ZK["Zookeeper :2181\nCoordenação do cluster"]

        subgraph BROKER["Kafka Broker :9091"]
            T_VENDAS["vendas-topico\npartition: 0 | replication: 1"]
            T_PAYMENT["payment-processed\npartition: 0 | replication: 1"]
        end

        KAFDROP["Kafdrop :9000\nUI de monitoramento"]

        ZK -->|coordena| BROKER
        KAFDROP -->|monitora| BROKER
    end

    subgraph PRODUCER_ORDER["📤 Order Service — Producer"]
        KPC["KafkaProducerConfig\n@Value bootstrap-servers"]
        KT_OS["KafkaTemplate&lt;String, String&gt;"]
        KPC --> KT_OS
        KT_OS -->|"send('vendas-topico', orderId)"| T_VENDAS
    end

    subgraph CONSUMER_PAYMENT["⚙️ Payment Service — Consumer + Producer"]
        KCC["KafkaConsumerConfig\n@Value bootstrap-servers\n@Value consumer.group-id"]
        KL_PS["@KafkaListener\ntopics=vendas-topico\ngroupId=payment-group"]
        KT_PS["KafkaTemplate&lt;String, String&gt;"]
        KCC --> KL_PS
        T_VENDAS -->|poll + commit offset| KL_PS
        KL_PS --> KT_PS
        KT_PS -->|"send('payment-processed', orderId, status)"| T_PAYMENT
    end

    subgraph CONSUMER_NOTIF["🔔 Notification Service — Consumer"]
        KL_NS["@KafkaListener\ntopics=payment-processed\ngroupId=notification-group"]
        T_PAYMENT -->|poll + commit offset| KL_NS
    end

    style DOCKER fill:#e3f2fd,stroke:#1565C0,color:#000
    style PRODUCER_ORDER fill:#e8f5e9,stroke:#2E7D32,color:#000
    style CONSUMER_PAYMENT fill:#fff8e1,stroke:#F57F17,color:#000
    style CONSUMER_NOTIF fill:#fce4ec,stroke:#C62828,color:#000
```

### 5.2 Consumer Groups — por que são independentes

```mermaid
graph LR
    subgraph TOPICO["vendas-topico — Partition 0"]
        MSG1["offset 40: order-001"]
        MSG2["offset 41: order-002"]
        MSG3["offset 42: order-003"]
    end

    subgraph G1["Consumer Group: payment-group"]
        PS2["Payment Service\noffset atual: 42"]
    end

    MSG1 & MSG2 & MSG3 -->|lê independentemente| PS2

    note1["Cada group mantém seu próprio offset.\nSe um novo consumer group for adicionado,\nele lê TODOS os eventos desde o início (earliest)."]

    style TOPICO fill:#e3f2fd,stroke:#1565C0,color:#000
    style G1 fill:#fff8e1,stroke:#F57F17,color:#000
```

### 5.3 Configuração Producer vs Consumer via application.yml

```mermaid
graph TD
    subgraph YML_ORDER["order-service/application.yml"]
        O1["kafka.bootstrap-servers: localhost:9092"]
        O2["kafka.producer.key-serializer: StringSerializer"]
        O3["kafka.producer.value-serializer: JsonSerializer"]
    end

    subgraph JAVA_ORDER["KafkaProducerConfig.java (order-service)"]
        OV["@Value bootstrap-servers"]
        OPF["ProducerFactory"]
        OKT["KafkaTemplate&lt;String,String&gt;"]
        OV --> OPF --> OKT
    end

    subgraph YML_PAYMENT["payment-service/application.yml"]
        P1["kafka.bootstrap-servers: localhost:9092"]
        P2["kafka.consumer.group-id: payment-group"]
        P3["kafka.consumer.key-deserializer: StringDeserializer"]
    end

    subgraph JAVA_PAYMENT["KafkaConsumerConfig.java + KafkaProducerConfig.java (payment-service)"]
        PV1["@Value bootstrap-servers"]
        PV2["@Value consumer.group-id"]
        PCF["ConsumerFactory"]
        PKT["KafkaTemplate&lt;String,String&gt;"]
        PV1 --> PCF & PKT
        PV2 --> PCF
    end

    subgraph YML_NOTIF["notification-service/application.yml"]
        N1["kafka.bootstrap-servers: localhost:9092"]
        N2["kafka.consumer.group-id: notification-group"]
    end

    subgraph JAVA_NOTIF["NotificationService.java"]
        NL["@KafkaListener\ntopics=payment-processed\ngroupId=notification-group"]
    end

    O1 -.->|lido por| OV
    P1 -.->|lido por| PV1
    P2 -.->|lido por| PV2
    N1 & N2 -.->|lido por| NL

    style JAVA_ORDER fill:#e8f5e9,stroke:#2E7D32,color:#000
    style JAVA_PAYMENT fill:#fff8e1,stroke:#F57F17,color:#000
    style JAVA_NOTIF fill:#fce4ec,stroke:#C62828,color:#000
```

---

## 6. Eventos de Domínio Internos (Spring Events)

Eventos síncronos dentro da JVM do `order-service`, sem rede.

```mermaid
sequenceDiagram
    participant OC as OrderController
    participant OS as OrderServiceImpl
    participant DB as MongoDB
    participant AEP as ApplicationEventPublisher
    participant OEL as OrderCreatedEventListener

    OC->>OS: save(order)
    OS->>DB: repository.save(order)
    DB-->>OS: Order { id: "abc123" }

    OS->>AEP: publishEvent(new OrderCreatedEvent(this, "abc123"))
    AEP->>OEL: onApplicationEvent(event)
    OEL->>OEL: log.info("Order created with ID: abc123")

    Note right of OEL: Próximo passo (Fase 2):<br/>publicar aqui no Kafka<br/>kafkaTemplate.send("vendas-topico", orderId)

    OS-->>OC: ResponseEntity.ok(order)
```

### Hierarquia de Classes — Spring Events

```mermaid
classDiagram
    class ApplicationEvent {
        <<Spring Framework>>
        +getSource() Object
        +getTimestamp() long
    }

    class OrderCreatedEvent {
        -String orderId
        +OrderCreatedEvent(source, orderId)
        +getOrderId() String
    }

    class ApplicationListener~E~ {
        <<interface>>
        +onApplicationEvent(E event) void
    }

    class OrderCreatedEventListener {
        +onApplicationEvent(OrderCreatedEvent) void
        +supportsAsyncExecution() boolean
    }

    ApplicationEvent <|-- OrderCreatedEvent
    ApplicationListener <|.. OrderCreatedEventListener
    OrderCreatedEventListener ..> OrderCreatedEvent : processa
```

---

## 7. Hierarquia de Classes — Abstrações CRUD

```mermaid
classDiagram
    direction TB

    class Rest~T~ {
        <<interface>>
        +save(T, String) ResponseEntity~T~
        +update(String id, T) ResponseEntity~T~
        +deleteById(String id) void
        +findById(String id) ResponseEntity~T~
        +list(Map) ResponseEntity~List~T~~
        +count(Map) ResponseEntity~Long~
    }

    class AbstractController~T~ {
        <<abstract>>
        #getService() AbstractService~T~
        +save() ResponseEntity~T~
        +update() ResponseEntity~T~
        +deleteById() void
        +findById() ResponseEntity~T~
        +list() ResponseEntity~List~T~~
        +count() ResponseEntity~Long~
    }

    class AbstractService~T~ {
        <<abstract>>
        #getRepository() MongoRepository~T,String~
        +save() ResponseEntity~T~
        +update() ResponseEntity~T~
        +deleteById() void
        +findById() ResponseEntity~T~
        +list() ResponseEntity~List~T~~
        +count() ResponseEntity~Long~
    }

    class OrderController {
        -OrderServiceImpl orderService
        #getService() AbstractService~Order~
    }

    class OrderServiceImpl {
        -OrderRepository orderRepository
        #getRepository() MongoRepository~Order,String~
    }

    class OrderRepository {
        <<interface>>
    }

    class MongoRepository~T,ID~ {
        <<Spring Data>>
    }

    Rest <|.. AbstractController
    Rest <|.. AbstractService
    AbstractController <|-- OrderController
    AbstractService <|-- OrderServiceImpl
    OrderController --> OrderServiceImpl : delega
    OrderServiceImpl --> OrderRepository : usa
    MongoRepository <|-- OrderRepository
```

---

## 8. Dependências entre Módulos

```mermaid
graph TD
    subgraph LIBS["📦 Bibliotecas Compartilhadas"]
        OM["order-model\nOrder, Product, OrderStatus, Metadata"]
        OU["order-utils\nExceptionHandler, ViaCEP, ResourceNotFoundException"]
        ORS["order-rest-service\nAbstractController, AbstractService, Rest"]
    end

    subgraph SERVICES2["⚙️ Serviços"]
        OS2["order-service"]
        IS2["inventory-service"]
        PS2["payment-service"]
        NS2["notification-service"]
    end

    OM --> OS2
    OM --> IS2
    OU --> OS2
    ORS --> OS2

    style LIBS fill:#f3e5f5,stroke:#7B1FA2,color:#000
    style SERVICES2 fill:#e3f2fd,stroke:#1565C0,color:#000
```

---

## 9. Fluxo de Dados no MongoDB

```mermaid
graph LR
    subgraph REQUEST["HTTP Request"]
        JSON_IN["POST /orderflow/v1/order\n{ customerId, items, totalAmount }"]
    end

    subgraph ORDER_SERVICE2["Order Service"]
        CTRL["OrderController"]
        SVC["OrderServiceImpl\n.save(order)"]
        REPO["OrderRepository\nextends MongoRepository"]
    end

    subgraph MONGODB2["MongoDB :27018"]
        subgraph COL_ORDER["Collection: orders"]
            DOC["{\n  _id: ObjectId,\n  customerId,\n  items: [...],\n  totalAmount,\n  status: DRAFT,\n  metadata: {\n    deleted: false,\n    tenantId,\n    correlationId\n  }\n}"]
        end
        subgraph COL_PRODUCT["Collection: products"]
            PROD["{\n  _id: ObjectId,\n  name,\n  price,\n  stockQuantity,\n  isActive\n}"]
        end
    end

    subgraph INVENTORY_SERVICE2["Inventory Service"]
        IREP["ProductRepository\nextends MongoRepository"]
    end

    JSON_IN --> CTRL --> SVC --> REPO --> DOC
    IREP --> PROD

    style MONGODB2 fill:#e8f5e9,stroke:#2E7D32,color:#000
    style ORDER_SERVICE2 fill:#e3f2fd,stroke:#1565C0,color:#000
    style INVENTORY_SERVICE2 fill:#fff8e1,stroke:#F57F17,color:#000
```

---

## Resumo da Arquitetura de Mensageria

```mermaid
graph LR
    subgraph SYNC["🔄 Síncrono (HTTP)"]
        A1["Cliente → Gateway → Order Service"]
        A2["Cliente → Gateway → Inventory Service"]
        A3["Order Service → ViaCEP (Feign)"]
    end

    subgraph KAFKA["⚡ Assíncrono — Kafka (tudo aqui)"]
        B1["vendas-topico\nOrder Service → Payment Service"]
        B2["payment-processed\nPayment Service → Notification Service"]
        B3["Alta throughput · Replay · Múltiplos consumers\nOrdem garantida por partição · Log imutável"]
    end

    subgraph INTERNAL["🔔 Interno — Spring ApplicationEvent"]
        D1["OrderServiceImpl → OrderCreatedEventListener"]
        D2["Síncrono, na mesma JVM, sem rede"]
    end

    style KAFKA fill:#e3f2fd,stroke:#1565C0,color:#000
    style SYNC fill:#e8f5e9,stroke:#2E7D32,color:#000
    style INTERNAL fill:#f3e5f5,stroke:#7B1FA2,color:#000
```

---

*Documento atualizado em: 2026-03-18*
*Para visualizar: VS Code (Mermaid Preview), GitHub, ou [mermaid.live](https://mermaid.live)*
