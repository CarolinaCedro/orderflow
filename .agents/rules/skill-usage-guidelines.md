# Skill Usage Guidelines — OrderFlow

Skills are step-by-step execution guides for common tasks in this project. Each skill file
documents the exact sequence of decisions, code patterns, and validations for one specific task.

---

## What Skills Are

A skill is a reusable, project-specific playbook. Skills are not generic tutorials — they are
opinionated guides tied to OrderFlow's actual architecture, entities, and patterns.

Skills live in: `.agents/skills/{category}/{skill-name}.md`

---

## When to Load a Skill

Load a skill BEFORE writing any code. Skills define the "how" for a task; knowledge files define
the "why". Both must be read.

| Task                                  | Skill to load                                |
|---------------------------------------|----------------------------------------------|
| Creating a new Order                  | `pedidos/criar-pedido.md`                    |
| Processing payment via Kafka          | `pedidos/processar-pagamento.md`             |
| Sending notifications post-payment    | `pedidos/enviar-notificacao.md`              |
| Publishing any Kafka event            | `kafka/publicar-evento.md`                   |
| Consuming any Kafka event             | `kafka/consumir-evento.md`                   |
| Building a dynamic MongoDB query      | `mongo/montar-query-dinamica.md`             |
| Implementing soft delete              | `mongo/soft-delete.md`                       |
| Generating JWT tokens                 | `security/gerar-token.md`                    |
| Applying RBAC with @PreAuthorize      | `security/aplicar-rbac.md`                   |
| Creating a new CRUD microservice      | `crud/novo-servico-crud.md`                  |

---

## How to Apply a Skill

1. Read the skill file top to bottom.
2. Follow each numbered step in order. Do not skip steps.
3. Use the code examples in the skill as templates — replace entity names, topics, etc.
4. After completing each step, verify the output matches the expected pattern described.
5. If the skill references another knowledge file (e.g., "see kafka.md for consumer factory"),
   read that section before proceeding.

---

## Skill File Structure

Every skill follows this structure:

```markdown
# Skill: {Title}

## Purpose
What this skill accomplishes.

## When to Use
Triggers / conditions for applying this skill.

## Prerequisites
What must already exist (entities, topics, services).

## Steps
1. Step one with code example
2. Step two with code example
...

## Validation Checklist
- [ ] Checklist item 1
- [ ] Checklist item 2

## Common Mistakes
- Mistake and correction
```

---

## Knowledge Base Reference Map

When a skill requires deeper understanding, it references knowledge files:

```
.agents/knowledge/
├── java/
│   ├── best-practices.md      → Java 17 patterns (records, sealed, var, streams)
│   ├── performance.md         → N+1, projections, batch Kafka, reactive blocking
│   └── exception-handling.md  → ResourceNotFoundException hierarchy, @ControllerAdvice
├── spring/
│   ├── boot.md                → Autoconfiguration, AutoConfiguration.imports, library POMs
│   ├── security.md            → JWT RS256, JwkSet, JwtAuthenticationConverter, @PreAuthorize
│   ├── data-mongodb.md        → MongoTemplate, Criteria, soft delete, @Document, indexes
│   ├── kafka.md               → @KafkaListener, KafkaTemplate, consumer factory, error handling
│   ├── cloud.md               → Eureka, Gateway, Config Server, Feign, Spring Cloud BOM
│   └── validation.md          → @Valid, @NotBlank, @Positive, nested validation, error response
├── architecture/
│   ├── microservices.md       → Service boundaries, shared libs, AbstractController pattern
│   ├── communication.md       → Kafka vs Feign, Gateway routing, no RestTemplate
│   ├── event-driven.md        → Event flow, payload structure, idempotency
│   └── resilience.md          → What's missing, Kafka natural retry, future Resilience4j
├── infra/
│   ├── docker.md              → MongoDB + Kafka compose files, port mapping, startup order
│   ├── kafka-setup.md         → bootstrap-servers, topic creation, Kafdrop, log flooding
│   └── mongo-setup.md         → URI, authSource=admin, collections, MongoTemplate config
└── orderflow/
    ├── pedidos.md             → Order entity, OrderStatus, ApprovalStatus, Metadata lifecycle
    ├── pagamento.md           → PaymentService, simulatePayment, payment-processed payload
    ├── notificacao.md         → NotificationService, email simulation, real email options
    └── seguranca.md           → order-security-server architecture, RBAC, login flow
```

---

## Rule Priority

When a skill conflicts with a rule, the rule wins. Rules are constraints; skills are guides.

Order of authority:
1. `.agents/rules/*.md` — non-negotiable constraints
2. `CLAUDE.md` — master anti-patterns and enforcement
3. `.agents/skills/*.md` — execution guidance (follows rules)
4. `.agents/knowledge/*.md` — reference material

---

## Creating New Skills

When a new task pattern emerges that will recur, document it as a skill:

1. Create the file in `.agents/skills/{category}/{skill-name}.md`.
2. Follow the skill file structure above.
3. Keep it project-specific — reference actual class names, topic names, and field paths.
4. Do not duplicate content from knowledge files — link to them instead.
5. Include at least one complete Java code example.

---

## Skills Are Not Documentation

Skills are execution playbooks for an AI agent or a developer following a task. They are not
README files, API documentation, or architecture diagrams. Keep them action-oriented.
