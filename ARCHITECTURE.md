# OrderFlow — Documentação de Arquitetura

> Sistema de gerenciamento de pedidos B2B com arquitetura de microserviços.

---

## Sumário

1. [Visão Geral](#1-visão-geral)
2. [Stack Tecnológico](#2-stack-tecnológico)
3. [Arquitetura de Serviços](#3-arquitetura-de-serviços)
4. [Segurança](#4-segurança)
5. [Domínio de Negócio](#5-domínio-de-negócio)
6. [Comunicação entre Serviços](#6-comunicação-entre-serviços)
7. [Fluxo do Pedido (Order Lifecycle)](#7-fluxo-do-pedido-order-lifecycle)
8. [Endpoints da API](#8-endpoints-da-api)
9. [Infraestrutura e Docker](#9-infraestrutura-e-docker)
10. [Como Rodar Localmente](#10-como-rodar-localmente)
11. [Padrões Arquiteturais](#11-padrões-arquiteturais)
12. [Guia de Contribuição](#12-guia-de-contribuição)
13. [Roadmap de Melhorias](#13-roadmap-de-melhorias)

---

## 1. Visão Geral

O **OrderFlow** é um sistema de gestão de pedidos projetado para operações B2B. Ele gerencia o ciclo de vida completo de um pedido: da criação, passando por aprovação e pagamento, até a notificação ao cliente.

### Características Principais

- Arquitetura de **microserviços** com Spring Cloud
- **Event-driven** com Apache Kafka
- **Service Discovery** dinâmico com Eureka
- **API Gateway** unificado com Spring Cloud Gateway
- **Configuração centralizada** com Config Server
- **Segurança JWT RS256** com RBAC (ADMIN, MANAGER, BUYER, VIEWER)
- Persistência em **MongoDB** (NoSQL)
- **Soft Delete** nativo com filtro automático nas queries
- **Validação de dados** via Jakarta Validation em todas as entidades
- **Auditoria completa** com metadados de criação e atualização
- **Swagger UI** em todos os serviços REST

---

## 2. Stack Tecnológico

| Camada | Tecnologia | Versão |
|---|---|---|
| Linguagem | Java | 17 |
| Framework Principal | Spring Boot | 3.4.3 |
| Cloud / Microserviços | Spring Cloud | 2024.0.1 |
| Service Registry | Spring Cloud Netflix Eureka | 4.2.x |
| API Gateway | Spring Cloud Gateway (WebFlux) | — |
| Config Server | Spring Cloud Config | — |
| Mensageria | Apache Kafka | — |
| Banco de Dados | MongoDB | — |
| ORM / Data | Spring Data MongoDB | — |
| Segurança | Spring Security 6.4.x + Nimbus JOSE JWT | — |
| HTTP Client | OpenFeign (ViaCEP) | — |
| Validação | Jakarta Validation (Hibernate Validator) | — |
| Documentação | Springdoc OpenAPI (Swagger UI) | 2.8.3 |
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
         ┌──────────┐ ┌──────────────▼──────────────┐
         │ Security │ │       API Gateway            │
         │ Server   │ │    Spring Cloud Gateway      │◄── CLIENT
         │  :9999   │ │         :8080                │
         └────▲─────┘ └──┬───────┬──────┬───────┬───┘
     JWKS/auth│           │       │      │       │
              │     ┌─────▼──┐ ┌──▼───┐ ┌▼────┐ ┌▼──────────┐
              │     │ Order  │ │Pay-  │ │Inv. │ │Notif.      │
              │     │Service │ │ment  │ │Serv.│ │Service     │
              │     │ :8081  │ │:8085 │ │:8089│ │:8083       │
              │     └───┬────┘ └──┬───┘ └──┬──┘ └──┬─────────┘
              │         │    Kafka │        │   Kafka│
              │    ┌────▼───┐ ┌───▼────────▼───────▼──┐
              └────┤MongoDB │ │        Kafka           │
                   │:27018  │ │ vendas-topico          │
                   └────────┘ │ payment-processed      │
                              │        :9091           │
                              └────────────────────────┘
```

### Módulos e Responsabilidades

| Módulo | Tipo | Porta | Responsabilidade |
|---|---|---|---|
| `gateway-server` | Serviço | 8080 | Ponto único de entrada. Valida JWT. Roteia via Eureka. |
| `order-service` | Serviço | 8081 | CRUD de pedidos. Publica em `vendas-topico`. |
| `notification-service` | Serviço | 8083 | Consome `payment-processed`. Simula envio de e-mail. |
| `payment-service` | Serviço | 8085 | Consome `vendas-topico`, publica em `payment-processed`. |
| `inventory-service` | Serviço | 8089 | CRUD de produtos com RBAC. |
| `eureka-server` | Infraestrutura | 8761 | Service registry. |
| `config-server` | Infraestrutura | 8888 | Configuração centralizada. |
| `order-security-server` | Serviço | 9999 | Auth server: `/auth/login` + `/.well-known/jwks.json`. |
| `order-model` | Biblioteca | — | Entidades de domínio compartilhadas. |
| `order-rest-service` | Biblioteca | — | AbstractController + AbstractService (CRUD genérico, filtros). |
| `order-utils` | Biblioteca | — | Exception handler, MicroserviceSecurityConfig (autoconfigured), SecurityContextHelper, ViaCEP. |

---

## 4. Segurança

### Modelo de Autenticação

O sistema utiliza **JWT RS256** com par de chaves RSA 2048 bits gerado pelo `order-security-server`.

```
1. Cliente → POST /order-security-server/auth/login
             { "username": "buyer1", "password": "buyer123" }

2. Security Server → { "token": "eyJ...", "expiresIn": 3600 }

3. Cliente → qualquer endpoint
             Authorization: Bearer eyJ...

4. Gateway valida JWT contra /.well-known/jwks.json antes de rotear

5. Serviço valida JWT novamente (defesa em profundidade)
```

### RBAC — Papéis e Permissões

| Role | Criar | Editar | Deletar | Visualizar |
|---|---|---|---|---|
| `ADMIN` | ✅ | ✅ | ✅ | ✅ |
| `MANAGER` | ✅ | ✅ | ❌ | ✅ |
| `BUYER` | ✅ | ❌ | ❌ | ✅ |
| `VIEWER` | ❌ | ❌ | ❌ | ✅ |

### Usuários de Teste (criados no startup)

| Usuário | Senha | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `manager1` | `manager123` | MANAGER |
| `buyer1` | `buyer123` | BUYER |

### Endpoints Públicos (sem token)

| Endpoint | Serviço |
|---|---|
| `POST /auth/login` | order-security-server |
| `GET /.well-known/jwks.json` | order-security-server |
| `GET /actuator/**` | todos |
| `GET /swagger-ui/**` | todos |
| `GET /v3/api-docs/**` | todos |

---

## 5. Domínio de Negócio

### Entidades Principais

#### Order (Pedido)
```
Order
├── id                 (String)          — @Id MongoDB
├── customerId         (String)          — @NotBlank
├── customerName       (String)          — @NotBlank
├── items              (List<OrderItem>) — @NotEmpty @Valid
├── totalAmount        (BigDecimal)      — @NotNull @Positive
├── status             (OrderStatus)
├── approvalStatus     (ApprovalStatus)
├── approvedBy         (String)
├── approvalDate       (LocalDateTime)
├── erpOrderId         (String)
└── metadata           (Metadata)
```

#### OrderItem
```
OrderItem
├── productId    (String)     — @NotBlank
├── productName  (String)     — @NotBlank
├── quantity     (Integer)    — @NotNull @Positive
├── unitPrice    (BigDecimal) — @NotNull @Positive
└── totalPrice   (BigDecimal)
```

#### Product (Produto)
```
Product
├── id             (String)     — @Id MongoDB
├── name           (String)     — @NotBlank
├── category       (String)     — @NotBlank
├── price          (BigDecimal) — @NotNull @Positive
├── stockQuantity  (Integer)    — @NotNull @Min(0)
├── description    (String)
├── brand / size / color / material
├── weight / rating / reviewCount
└── isActive       (Boolean)    — default: true
```

#### AppUser (Usuário)
```
AppUser
├── id       (String)    — @Id MongoDB
├── username (String)
├── password (String)    — BCrypt
├── roles    (Set<Role>) — ADMIN, MANAGER, BUYER, VIEWER
└── active   (boolean)
```

#### Metadata (Auditoria)
```
Metadata
├── createdAt / createdBy
├── updatedAt / updatedBy
├── deletedAt / deletedBy
├── deleted        (Boolean) — soft delete flag, filtrado automaticamente
├── correlationId
├── tenantId       — multitenancy
└── version        — controle otimista
```

### Enumerações

#### OrderStatus
`DRAFT` → `PENDING_APPROVAL` → `APPROVED` / `REJECTED` → `SENT_TO_ERP` → `COMPLETED` / `CANCELLED`

#### ApprovalStatus
`NOT_REQUIRED` | `PENDING` | `APPROVED` | `REJECTED` | `REVISION_REQUESTED`

---

## 6. Comunicação entre Serviços

### Kafka (Event Streaming)

| Tópico | Producer | Consumer(s) | Payload |
|---|---|---|---|
| `vendas-topico` | order-service | payment-service | `orderId` (String) |
| `payment-processed` | payment-service | notification-service, order-service | `{orderId, status, processedAt}` (JSON) |

**Configuração:** Bootstrap Servers `localhost:9091` | Kafdrop UI `http://localhost:9000`

### OpenFeign (HTTP Síncrono)

| Client | URL | Uso |
|---|---|---|
| `ViaCep` | `https://viacep.com.br/ws/` | Busca endereço por CEP |

---

## 7. Fluxo do Pedido (Order Lifecycle)

```
1. AUTENTICAÇÃO
   POST /order-security-server/auth/login → JWT

2. CRIAÇÃO DO PEDIDO
   POST /order-service/orderflow/v1/order  (Bearer <jwt>, role: BUYER+)
   └── Salva no MongoDB com status PENDING_APPROVAL
   └── Publica orderId em vendas-topico

3. PROCESSAMENTO DE PAGAMENTO (assíncrono)
   payment-service consome vendas-topico
   └── Simula pagamento → PAYMENT_SUCCESS
   └── Publica {orderId, status, processedAt} em payment-processed

4. ATUALIZAÇÃO DO PEDIDO (assíncrono)
   order-service consome payment-processed
   └── PAYMENT_SUCCESS → status=COMPLETED, approvalStatus=APPROVED
   └── PAYMENT_FAILURE → status=CANCELLED, approvalStatus=REJECTED

5. NOTIFICAÇÃO (assíncrono)
   notification-service consome payment-processed
   └── Loga simulação de e-mail ao cliente
```

---

## 8. Endpoints da API

Todos acessíveis via Gateway (`http://localhost:8080`) com `Authorization: Bearer <token>`.

### Auth — order-security-server (público)

| Método | Endpoint via Gateway | Descrição |
|---|---|---|
| `POST` | `/order-security-server/auth/login` | Obter JWT |
| `GET` | `/order-security-server/.well-known/jwks.json` | Chave pública |

### Order Service

Via Gateway: `http://localhost:8080/order-service/orderflow/v1/order`

| Método | Endpoint | Role Mínima |
|---|---|---|
| `POST` | `/orderflow/v1/order` | BUYER |
| `GET` | `/orderflow/v1/order` | VIEWER |
| `GET` | `/orderflow/v1/order/{id}` | VIEWER |
| `GET` | `/orderflow/v1/order/count` | VIEWER |
| `PUT` | `/orderflow/v1/order/{id}` | MANAGER |
| `DELETE` | `/orderflow/v1/order/{id}` | ADMIN |

**Filtros dinâmicos:** `GET /orderflow/v1/order?status=PENDING_APPROVAL&customerId=123`

**Exemplo — Criar Pedido:**
```json
POST /orderflow/v1/order
Authorization: Bearer eyJ...
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
  "totalAmount": 999.00
}
```

### Inventory Service

Via Gateway: `http://localhost:8080/inventory-service/orderflow/v1/product`

| Método | Endpoint | Role Mínima |
|---|---|---|
| `POST` | `/orderflow/v1/product` | MANAGER |
| `GET` | `/orderflow/v1/product` | VIEWER |
| `GET` | `/orderflow/v1/product/{id}` | VIEWER |
| `GET` | `/orderflow/v1/product/count` | VIEWER |
| `PUT` | `/orderflow/v1/product/{id}` | MANAGER |
| `DELETE` | `/orderflow/v1/product/{id}` | ADMIN |

### Monitoramento

| Interface | URL |
|---|---|
| Eureka Dashboard | `http://localhost:8761` |
| Kafdrop (Kafka UI) | `http://localhost:9000` |
| Swagger — order-service | `http://localhost:8081/swagger-ui/index.html` |
| Swagger — inventory-service | `http://localhost:8089/swagger-ui/index.html` |
| Swagger — security-server | `http://localhost:9999/swagger-ui/index.html` |

---

## 9. Infraestrutura e Docker

### MongoDB
```
Container: products-db
Porta:     27018 (externo) → 27017 (interno)
Database:  orderflow
Username:  root / Password: products
```

### Kafka + Zookeeper + Kafdrop
```
Zookeeper:  2181
Kafka:      9091 (externo) → 19091 (interno Docker)
Kafdrop UI: 9000
```

### Iniciar Infraestrutura
```bash
cd @docker-compose/mongo-db && docker compose up -d
cd @docker-compose/kafka    && docker compose up -d
```

---

## 10. Como Rodar Localmente

### Ordem de Inicialização

```
1. MongoDB + Kafka (Docker)
2. eureka-server      :8761
3. config-server      :8888
4. order-security-server :9999
5. order-service      :8081
6. payment-service    :8085
7. notification-service :8083
8. inventory-service  :8089
9. gateway-server     :8080
```

### Comandos
```bash
# Build completo
mvn clean install -DskipTests

# Iniciar serviço
cd <nome-do-servico>
mvn spring-boot:run
```

### Teste rápido do fluxo completo
```bash
# 1. Login
curl -X POST http://localhost:9999/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"buyer1","password":"buyer123"}'

# 2. Criar pedido (substituir <token>)
curl -X POST http://localhost:8080/order-service/orderflow/v1/order \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{ "customerId":"c1", "customerName":"Empresa X", "items":[{"productId":"p1","productName":"Produto A","quantity":2,"unitPrice":50.00,"totalPrice":100.00}], "totalAmount":100.00 }'
```

---

## 11. Padrões Arquiteturais

### Template Method — AbstractController / AbstractService

```
Rest<T> (interface)
    ├── AbstractController<T>   ← Controllers estendem
    │       └── delega para → AbstractService<T>
    └── AbstractService<T>      ← Services estendem
            ├── MongoTemplate   → filtros dinâmicos + soft delete
            └── MongoRepository → CRUD básico
```

`AbstractService` aplica automaticamente:
- Filtro `metadata.deleted != true` em todo `list()` e `count()`
- Filtros dinâmicos via query params: `?campo=valor`

### Event-Driven Architecture

```
order-service ──[vendas-topico]──► payment-service
                                          │
                              [payment-processed]
                                    ┌─────┴──────┐
                             order-service   notification-service
                           (atualiza status) (simula e-mail)
```

### Segurança em Camadas

```
Internet → Gateway (valida JWT) → Serviço (valida JWT + @PreAuthorize)
```

### Soft Delete

`metadata.deleted = true` marca o registro. Nunca deletado fisicamente.
`AbstractService` filtra automaticamente registros deletados em todas as listagens.

### Multitenancy

Campo `metadata.tenantId` em todas as entidades para suporte multi-empresa.

---

## 12. Guia de Contribuição

### Estrutura de Pacotes

```
org.cedro.<nome-do-servico>/
├── controller/
├── service/
│   └── impl/
├── repository/
├── config/
├── listeners/
└── init/
```

### Gitflow

```
master     → produção
develop    → integração
feature/*  → novas features
hotfix/*   → correções urgentes
```

### Commits (Conventional Commits)

```
feat: adiciona endpoint de aprovação de pedidos
fix: corrige filtro de soft delete no AbstractService
refactor: extrai lógica de pagamento para PaymentProcessor
docs: atualiza ARCHITECTURE.md
test: adiciona testes unitários para OrderService
```

---

## 13. Roadmap de Melhorias

### ✅ Concluído

- [x] CRUD genérico com AbstractController/AbstractService
- [x] Kafka end-to-end (order → payment → notification → order update)
- [x] Soft delete com filtro automático nas queries
- [x] Filtros dinâmicos por query param
- [x] Validação de dados com Jakarta Validation
- [x] JWT RS256 + RBAC completo
- [x] Gateway como resource server (WebFlux)
- [x] MicroserviceSecurityConfig autoconfigured via order-utils
- [x] @PreAuthorize nos controllers (order-service, inventory-service)
- [x] Swagger/OpenAPI em todos os serviços REST
- [x] inventory-service com CRUD completo de produtos
- [x] Metadados automáticos (createdAt, updatedAt) via MongoEventListener

### 🔴 Pendente — Alta Prioridade

- [ ] **Testes** — unitários (Mockito) e integração (Testcontainers)
- [ ] **DTOs** — separar entidades de domínio dos contratos de API
- [ ] **Paginação** — `Page<T>` + `Pageable` no AbstractService

### 🟡 Pendente — Média Prioridade

- [ ] **Circuit Breaker** — Resilience4j (Retry, Rate Limiter)
- [ ] **Tracing distribuído** — Micrometer Tracing + Zipkin
- [ ] **Rate Limiting** — Spring Cloud Gateway + Redis
- [ ] **CORS** — política no Gateway
- [ ] **Índices MongoDB** — `@Indexed` em customerId, status, tenantId

### 🚀 CI/CD

- [ ] GitHub Actions — build + testes em cada push
- [ ] Dockerfile por serviço
- [ ] Push de imagens para GitHub Container Registry

---

*Atualizado em: 2026-03-19 | Branch: feature/implementation-security*
