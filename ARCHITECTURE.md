# OrderFlow — Documentação de Arquitetura

> Sistema de gerenciamento de pedidos B2B com arquitetura de microserviços.

---

## Sumário

1. [Visão Geral](#1-visão-geral)
2. [Stack Tecnológico](#2-stack-tecnológico)
3. [Arquitetura de Serviços](#3-arquitetura-de-serviços)
4. [Domínio de Negócio](#4-domínio-de-negócio)
5. [Comunicação entre Serviços](#5-comunicação-entre-serviços)
6. [Fluxo do Pedido (Order Lifecycle)](#6-fluxo-do-pedido-order-lifecycle)
7. [Endpoints da API](#7-endpoints-da-api)
8. [Infraestrutura e Docker](#8-infraestrutura-e-docker)
9. [Como Rodar Localmente](#9-como-rodar-localmente)
10. [Variáveis de Ambiente](#10-variáveis-de-ambiente)
11. [Padrões Arquiteturais](#11-padrões-arquiteturais)
12. [Guia de Contribuição](#12-guia-de-contribuição)
13. [Roadmap de Melhorias](#13-roadmap-de-melhorias)

---

## 1. Visão Geral

O **OrderFlow** é um sistema de gestão de pedidos projetado para operações B2B. Ele gerencia o ciclo de vida completo de um pedido: da criação, passando por aprovação, pagamento, atualização de estoque, até a notificação e integração com ERP externo.

### Características Principais

- Arquitetura de **microserviços** com Spring Cloud
- **Event-driven** com Apache Kafka e RabbitMQ
- **Service Discovery** dinâmico com Eureka
- **API Gateway** unificado com Spring Cloud Gateway
- **Configuração centralizada** com Config Server
- Persistência em **MongoDB** (NoSQL)
- **Multitenancy** e **Soft Delete** nativos no modelo de dados
- **Auditoria completa** com metadados de criação, atualização e deleção

---

## 2. Stack Tecnológico

| Camada | Tecnologia | Versão |
|---|---|---|
| Linguagem | Java | 17 |
| Framework Principal | Spring Boot | 3.4.x |
| Cloud / Microserviços | Spring Cloud | 2024.0.x |
| Service Registry | Spring Cloud Netflix Eureka | 4.2.x |
| API Gateway | Spring Cloud Gateway | — |
| Config Server | Spring Cloud Config | — |
| Mensageria (Streaming) | Apache Kafka | 5.3.x (Confluent) |
| Mensageria (Queue) | RabbitMQ (AMQP) | — |
| Banco de Dados | MongoDB | latest |
| ORM / Data | Spring Data MongoDB | — |
| HTTP Client | OpenFeign | — |
| Boilerplate | Lombok | — |
| Monitoramento | Spring Boot Actuator | — |
| Containerização | Docker + Docker Compose | — |
| Build | Maven | 3 (Wrapper) |

---

## 3. Arquitetura de Serviços

```
                          ┌─────────────────────────────┐
                          │       Config Server          │
                          │         :8888                │
                          └──────────────┬──────────────┘
                                         │ configuração
                          ┌──────────────▼──────────────┐
                          │       Eureka Server          │
                          │    (Service Registry)        │
                          │         :8761                │
                          └──────────────┬──────────────┘
                                         │ discovery
                          ┌──────────────▼──────────────┐
         CLIENT ─────────►│       API Gateway            │
                          │    Spring Cloud Gateway      │
                          │         :8080                │
                          └──┬───────┬──────┬───────┬───┘
                             │       │      │       │
               ┌─────────────▼─┐ ┌───▼───┐ ┌▼────┐ ┌▼──────────────┐
               │ Order Service  │ │Payment│ │Inven│ │Notification    │
               │    :8081       │ │:8085  │ │:8089│ │Service :8083   │
               └───────┬───────┘ └───┬───┘ └──┬──┘ └──────┬────────┘
                       │             │         │            │
               ┌───────▼──────┐  ┌──▼────┐  ┌─▼──────┐   │
               │   MongoDB    │  │ Kafka │  │MongoDB │   │
               │  (orders)    │  │:9091  │  │(products│  RabbitMQ
               └──────────────┘  └───────┘  └────────┘  (paymentQueue)

               ┌──────────────────────────────────────────────────┐
               │              Order Security Server                │
               │                  (WIP)                           │
               └──────────────────────────────────────────────────┘
```

### Módulos e Responsabilidades

| Módulo | Tipo | Porta | Responsabilidade |
|---|---|---|---|
| `gateway-server` | Serviço | 8080 | Ponto único de entrada. Roteia requisições para os serviços via Eureka. |
| `order-service` | Serviço | 8081 | CRUD de pedidos. Lógica de negócio central. Publica eventos no Kafka. |
| `notification-service` | Serviço | 8083 | Consome mensagens RabbitMQ (`paymentQueue`) e envia notificações. |
| `payment-service` | Serviço | 8085 | Consome eventos Kafka (`vendas-topico`) e processa pagamentos. |
| `inventory-service` | Serviço | 8089 | Gerencia catálogo de produtos e controle de estoque. |
| `eureka-server` | Infraestrutura | 8761 | Service registry. Todos os serviços se registram aqui. |
| `config-server` | Infraestrutura | 8888 | Configuração centralizada para todos os serviços. |
| `order-security-server` | Serviço | — | Autenticação e autorização (em desenvolvimento). |
| `order-model` | Biblioteca | — | Entidades de domínio compartilhadas entre serviços. |
| `order-rest-service` | Biblioteca | — | Abstrações genéricas de CRUD (AbstractController, AbstractService). |
| `order-utils` | Biblioteca | — | Exceções, exception handler global, integração ViaCEP. |

---

## 4. Domínio de Negócio

### Entidades Principais

#### Order (Pedido)
Entidade central do sistema. Representa um pedido de compra B2B.

```
Order
├── id                 (String)         — Identificador único (MongoDB ObjectId)
├── customerId         (String)         — ID do cliente
├── customerName       (String)         — Nome do cliente
├── items              (List<OrderItem>) — Itens do pedido
├── totalAmount        (BigDecimal)     — Valor total
├── status             (OrderStatus)    — Status do pedido
├── approvalStatus     (ApprovalStatus) — Status de aprovação
├── approvedBy         (String)         — Usuário que aprovou
├── approvalDate       (LocalDateTime)  — Data de aprovação
├── erpOrderId         (String)         — ID no ERP externo
└── metadata           (Metadata)       — Metadados de auditoria
```

#### OrderItem (Item do Pedido)
```
OrderItem
├── productId    (String)     — ID do produto no catálogo
├── productName  (String)     — Nome do produto
├── quantity     (Integer)    — Quantidade solicitada
├── unitPrice    (BigDecimal) — Preço unitário
└── totalPrice   (BigDecimal) — Preço total do item
```

#### Product (Produto)
Coleção MongoDB: `products` (gerenciada pelo Inventory Service)

```
Product
├── id             (String)     — Identificador único
├── name           (String)     — Nome do produto
├── description    (String)     — Descrição
├── category       (String)     — Categoria
├── price          (BigDecimal) — Preço de venda
├── stockQuantity  (Integer)    — Quantidade em estoque
├── brand          (String)     — Marca
├── size           (String)     — Tamanho
├── color          (String)     — Cor
├── material       (String)     — Material
├── weight         (Double)     — Peso
├── rating         (Double)     — Avaliação média
├── reviewCount    (Integer)    — Número de avaliações
└── isActive       (Boolean)    — Produto ativo
```

#### Metadata (Auditoria e Controle)
Campo presente em todas as entidades principais.

```
Metadata
├── createdBy      (String)        — Usuário criador
├── createdAt      (LocalDateTime) — Data de criação
├── updatedBy      (String)        — Último usuário a atualizar
├── updatedAt      (LocalDateTime) — Data da última atualização
├── deletedBy      (String)        — Usuário que deletou
├── deletedAt      (LocalDateTime) — Data de deleção (soft delete)
├── deleted        (Boolean)       — Flag de soft delete (default: false)
├── correlationId  (String)        — ID de rastreamento da requisição
├── tenantId       (String)        — ID do tenant (multitenancy)
└── version        (Long)          — Versão para controle otimista
```

### Enumerações

#### OrderStatus — Ciclo de Vida do Pedido
| Status | Descrição |
|---|---|
| `DRAFT` | Pedido em rascunho, ainda editável |
| `PENDING_APPROVAL` | Aguardando aprovação |
| `APPROVED` | Aprovado, pronto para processamento |
| `REJECTED` | Reprovado na aprovação |
| `SENT_TO_ERP` | Enviado ao sistema ERP externo |
| `COMPLETED` | Pedido concluído com sucesso |
| `CANCELLED` | Pedido cancelado |

#### ApprovalStatus — Status de Aprovação
| Status | Descrição |
|---|---|
| `NOT_REQUIRED` | Aprovação não necessária |
| `PENDING` | Aguardando aprovação |
| `APPROVED` | Aprovado |
| `REJECTED` | Reprovado |
| `REVISION_REQUESTED` | Revisão solicitada pelo aprovador |

---

## 5. Comunicação entre Serviços

### Kafka (Event Streaming)

Usado para comunicação assíncrona de alta throughput entre serviços.

| Tópico | Producer | Consumer | Descrição |
|---|---|---|---|
| `vendas-topico` | Order Service | Payment Service | Evento de pedido criado/aprovado para processamento de pagamento |

**Configuração:**
- Bootstrap Servers: `localhost:9091` (externo), `kafka1:19091` (interno Docker)
- Serialização: `StringSerializer` / `StringDeserializer`
- UI (Kafdrop): `http://localhost:9000`

### RabbitMQ (Message Queue)

Usado para notificações assíncronas.

| Queue | Producer | Consumer | Descrição |
|---|---|---|---|
| `paymentQueue` | Payment Service | Notification Service | Status do processamento de pagamento |

### OpenFeign (HTTP Client)

| Client | URL | Uso |
|---|---|---|
| `ViaCep` | `https://viacep.com.br/ws/` | Busca endereço por CEP |

### Spring Application Events (Interno)

| Evento | Publisher | Listener | Descrição |
|---|---|---|---|
| `OrderCreatedEvent` | Order Service | `OrderCreatedEventListener` | Evento interno ao criar pedido |

---

## 6. Fluxo do Pedido (Order Lifecycle)

```
1. CRIAÇÃO
   Cliente ──► POST /orderflow/v1/order
              └── Order Service cria pedido com status DRAFT
              └── Persiste no MongoDB (collection: orders)
              └── Publica OrderCreatedEvent (interno)

2. SUBMISSÃO PARA APROVAÇÃO
   ──► PUT /orderflow/v1/order/{id}  { status: PENDING_APPROVAL }
       └── Order Service atualiza status

3. APROVAÇÃO / REJEIÇÃO
   Aprovador ──► PUT /orderflow/v1/order/{id}  { approvalStatus: APPROVED/REJECTED }
                └── Registra approvedBy + approvalDate
                └── Atualiza status para APPROVED ou REJECTED

4. PROCESSAMENTO DE PAGAMENTO
   ──► Order Service publica evento no Kafka (vendas-topico)
       └── Payment Service consome e processa pagamento
       └── Payment Service publica resultado no RabbitMQ (paymentQueue)

5. NOTIFICAÇÃO
   ──► Notification Service consome paymentQueue
       └── Envia notificação ao cliente (email/SMS/push)

6. ATUALIZAÇÃO DE ESTOQUE
   ──► Inventory Service reduz stockQuantity dos produtos

7. INTEGRAÇÃO ERP
   ──► Order Service atualiza erpOrderId
       └── Status: SENT_TO_ERP → COMPLETED
```

---

## 7. Endpoints da API

Todos os endpoints são acessíveis via **API Gateway** (`http://localhost:8080`).
O Gateway usa Service Discovery automático: o prefixo da rota é o nome do serviço em lowercase.

### Order Service — `http://localhost:8081`

Via Gateway: `http://localhost:8080/order-service/...`

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/orderflow/v1/order` | Criar novo pedido |
| `GET` | `/orderflow/v1/order` | Listar todos os pedidos |
| `GET` | `/orderflow/v1/order/{id}` | Buscar pedido por ID |
| `GET` | `/orderflow/v1/order/count` | Contar total de pedidos |
| `PUT` | `/orderflow/v1/order/{id}` | Atualizar pedido |
| `DELETE` | `/orderflow/v1/order/{id}` | Deletar pedido |

**Exemplo — Criar Pedido:**
```json
POST /orderflow/v1/order
{
  "customerId": "cust-001",
  "customerName": "Empresa XYZ",
  "items": [
    {
      "productId": "prod-001",
      "productName": "Produto A",
      "quantity": 10,
      "unitPrice": 99.90,
      "totalPrice": 999.00
    }
  ],
  "totalAmount": 999.00,
  "status": "DRAFT",
  "approvalStatus": "NOT_REQUIRED"
}
```

### Inventory Service — `http://localhost:8089`

Via Gateway: `http://localhost:8080/inventory-service/...`

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/` | Cadastrar produto |
| `GET` | `/` | Listar produtos |
| `GET` | `/{id}` | Buscar produto por ID |
| `PUT` | `/{id}` | Atualizar produto |
| `DELETE` | `/{id}` | Remover produto |

### Interfaces de Monitoramento

| Interface | URL | Descrição |
|---|---|---|
| Eureka Dashboard | `http://localhost:8761` | Serviços registrados |
| Kafdrop (Kafka UI) | `http://localhost:9000` | Tópicos e mensagens Kafka |
| Actuator | `http://localhost:808x/actuator` | Health, métricas |

---

## 8. Infraestrutura e Docker

### MongoDB

```yaml
# @docker-compose/mongo-db/docker-compose.yml
Container: products-db
Porta:     27018 (externo) → 27017 (interno)
Username:  root
Password:  products
```

### Kafka + Zookeeper + Kafdrop

```yaml
# @docker-compose/kafka/docker-compose.yml
Zookeeper:  2181
Kafka:      9091 (externo) → 19091 (interno Docker)
Kafdrop UI: 9000
```

### Iniciar Infraestrutura

```bash
# MongoDB
cd @docker-compose/mongo-db
docker-compose up -d

# Kafka
cd @docker-compose/kafka
docker-compose up -d
```

---

## 9. Como Rodar Localmente

### Pré-requisitos

- Java 17+
- Maven 3.8+ (ou use o wrapper `./mvnw`)
- Docker + Docker Compose

### Ordem de Inicialização

Os serviços devem ser iniciados na seguinte ordem:

```
1. Infraestrutura (MongoDB + Kafka)
2. Config Server    (:8888)
3. Eureka Server    (:8761)
4. Gateway Server   (:8080)
5. Order Service    (:8081)
6. Payment Service  (:8085)
7. Inventory Service(:8089)
8. Notification Service (:8083)
9. Order Security Server
```

### Comandos

```bash
# Clonar o repositório
git clone <repo-url>
cd orderflow

# Subir infraestrutura
cd @docker-compose/mongo-db && docker-compose up -d && cd ../..
cd @docker-compose/kafka && docker-compose up -d && cd ../..

# Build de todos os módulos
./mvnw clean install -DskipTests

# Iniciar cada serviço (em terminais separados)
cd config-server    && ../mvnw spring-boot:run
cd eureka-server    && ../mvnw spring-boot:run
cd gateway-server   && ../mvnw spring-boot:run
cd order-service    && ../mvnw spring-boot:run
cd payment-service  && ../mvnw spring-boot:run
cd inventory-service && ../mvnw spring-boot:run
cd notification-service && ../mvnw spring-boot:run

# Executar testes
./mvnw test
```

---

## 10. Variáveis de Ambiente

> **Atenção:** As configurações abaixo ainda estão hardcoded em alguns serviços (ver Roadmap de Melhorias). O objetivo é externalizá-las via Config Server ou variáveis de ambiente.

| Variável | Valor Padrão | Descrição |
|---|---|---|
| `MONGO_URI` | `mongodb://root:products@localhost:27018` | URI de conexão MongoDB |
| `MONGO_DB` | `orderflow` | Nome do banco |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9091` | Endereço do Kafka |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | URL do Eureka |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | URL do Config Server |
| `RABBITMQ_HOST` | `localhost` | Host do RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Porta do RabbitMQ |

---

## 11. Padrões Arquiteturais

### Template Method — AbstractController / AbstractService

Padrão que elimina boilerplate de CRUD. Cada serviço herda as operações genéricas.

```
Rest<T> (interface)
    ├── AbstractController<T>   ←── Controllers estendem isso
    │       └── delega para ──► AbstractService<T>
    └── AbstractService<T>      ←── Services estendem isso
            └── usa ──► MongoRepository<T, String>
```

### Event-Driven Architecture

```
Order Service ──[Kafka: vendas-topico]──► Payment Service
                                                │
                                    [RabbitMQ: paymentQueue]
                                                │
                                         Notification Service
```

### Multitenancy

Implementado via campo `metadata.tenantId` em todas as entidades. Cada requisição deve carregar o tenant no contexto.

### Soft Delete

Registros nunca são deletados fisicamente. O campo `metadata.deleted = true` marca o registro como deletado.

### Auditoria

Todas as operações de criação, atualização e deleção são registradas automaticamente via `Metadata`.

### Correlation ID

Campo `metadata.correlationId` permite rastrear uma requisição por todos os serviços (implementação de tracing manual, a ser evoluído para Micrometer Tracing).

---

## 12. Guia de Contribuição

### Estrutura de Pacotes Java

```
org.cedro.<nome-do-servico>/
├── <NomeDoServicoApplication>.java
├── controller/
│   └── <Entidade>Controller.java
├── service/
│   ├── <Entidade>Service.java      (interface)
│   └── impl/
│       └── <Entidade>ServiceImpl.java
├── repository/
│   └── <Entidade>Repository.java
├── config/
│   └── (configurações específicas)
├── events/
│   └── (eventos de domínio)
└── listeners/
    └── (listeners de eventos)
```

### Convenções de Nomenclatura

- Classes em **PascalCase** inglês
- Métodos e variáveis em **camelCase** inglês
- Constantes em **UPPER_SNAKE_CASE**
- Endpoints REST em **kebab-case** minúsculo
- Tópicos Kafka em **kebab-case**: `order-created`, `payment-processed`
- Queues RabbitMQ em **camelCase**: `paymentQueue`

### Gitflow

```
master         ─── produção
develop        ─── integração
feature/*      ─── novas features
hotfix/*       ─── correções urgentes em produção
release/*      ─── preparação de release
```

### Commits

Seguir **Conventional Commits**:
```
feat: adiciona endpoint de aprovação de pedidos
fix: corrige cálculo de totalAmount com desconto
refactor: extrai lógica de validação para OrderValidator
docs: atualiza documentação de endpoints
test: adiciona testes unitários para OrderService
```

---

## 13. Roadmap de Melhorias

### Fase 1 — Fundação Sólida (Imediato)

- [ ] Corrigir typos de nomenclatura (`PaymenteService`, `OrdemController`, package `controler`)
- [ ] Padronizar groupId Maven para `org.cedro.orderflow`
- [ ] Substituir `System.out.println` por `@Slf4j` + `log.info/error`
- [ ] Externalizar configurações hardcoded para `application.yml`
- [ ] Adicionar `@Valid` + Bean Validation nas entidades e controllers
- [ ] Implementar filtro de soft delete no `AbstractService`
- [ ] Preencher `GROUP_ID_CONFIG` vazio no `KafkaConsumerConfig` do payment-service

### Fase 2 — Qualidade e Contratos (Curto Prazo)

- [ ] Criar camada de DTOs separada das entidades de domínio
- [ ] Adicionar MapStruct para mapeamento DTO ↔ Entidade
- [ ] Implementar paginação (`Page<T>` + `Pageable`) em todas as listagens
- [ ] Adicionar springdoc-openapi (Swagger UI em `/swagger-ui.html`)
- [ ] Testes unitários com JUnit5 + Mockito
- [ ] Testes de integração com Testcontainers (MongoDB + Kafka)

### Fase 3 — Resiliência e Observabilidade (Médio Prazo)

- [ ] Implementar Resilience4j (Circuit Breaker, Retry, Rate Limiter por serviço)
- [ ] Adicionar Micrometer Tracing + Zipkin para tracing distribuído
- [ ] Propagar `correlationId` via headers HTTP entre serviços
- [ ] Expor métricas via Actuator + Prometheus
- [ ] Configurar health checks granulares (MongoDB, Kafka, RabbitMQ)

### Fase 4 — Segurança (Médio Prazo)

- [ ] Implementar JWT no `order-security-server`
- [ ] Configurar OAuth2 Resource Server nos microserviços
- [ ] Validar token no Gateway antes de rotear requisições
- [ ] Implementar RBAC para fluxo de aprovação de pedidos
- [ ] Adicionar Rate Limiting no Gateway com Redis
- [ ] Configurar política de CORS

### Fase 5 — Consistência e Produção (Longo Prazo)

- [ ] Implementar Transactional Outbox Pattern (atomicidade Kafka + MongoDB)
- [ ] Adicionar Schema Registry (Avro) para contratos de eventos Kafka
- [ ] Implementar Idempotency Key nas operações de criação
- [ ] Criar índices MongoDB (`customerId`, `status`, `tenantId`, `metadata.deleted`)
- [ ] Criar Docker Compose unificado para todos os serviços
- [ ] Preparar para deploy em Kubernetes (Helm Charts, ConfigMaps, Secrets)

---

*Documento gerado em: 2026-03-18*
*Branch: feature/crud-v1*
