# 🧠 CLAUDE.md — OrderFlow Agent Brain (Optimized)

This file is the master instruction set for any AI agent working on the OrderFlow project.

You MUST read this file before performing any task.

DO NOT load the entire `.agents/` directory.

You MUST load only the necessary files based on the task context.

---

## Safety Loading Rule

Minimal loading does NOT mean incomplete loading.

You MUST:
- load ALL required files for the task
- ensure no critical knowledge is missing

If unsure whether something is needed:
- LOAD IT

Never sacrifice correctness for token efficiency.

# 🎯 Core Objective

Ensure that ALL generated code:

* strictly follows `.agents/` standards
* respects architecture boundaries
* avoids hallucinations
* minimizes token usage

---

# ⚡ Performance & Token Efficiency Rules

## Smart Loading (MANDATORY)

* DO NOT load all `.agents/knowledge/` files
* DO NOT load all `.agents/rules/`
* DO NOT load all `.agents/skills/`

You MUST:

1. Identify the task type FIRST
2. Load ONLY the required files for that task
3. Ignore unrelated domains completely

Loading unnecessary files is FORBIDDEN.

---

## Context-Aware Loading

* Load only what is strictly necessary
* Avoid redundant reads
* Prefer minimal context over completeness

---

## Knowledge Reuse Rule

If knowledge was already used in the current conversation:

* DO NOT reload it again
* reuse previous context

---
## Task Classification (STRICT)

You MUST classify the task into ONE of the following:

- REST_API
- KAFKA_PRODUCER
- KAFKA_CONSUMER
- MONGODB_QUERY
- SECURITY
- NEW_MICROSERVICE
- BUG_FIX
- INFRASTRUCTURE

If unsure:
- ask for clarification
- DO NOT proceed

## Inline Priority Rule

If a skill already contains the required rules:

* DO NOT load additional knowledge files
* prefer using the skill directly

---

# 🧠 Mandatory Pre-Task Protocol

Before writing ANY code:

1. Read this file completely
2. Identify task type (CRUD, Kafka, Mongo, Security, etc.)
3. Load ONLY minimal required knowledge files
4. Load relevant skill (if exists)
5. Apply matching rules from `.agents/rules/`
6. Execute using known patterns only
7. Validate output before finishing

Skipping steps is FORBIDDEN.

---

# 🔁 Execution Flow

```
IDENTIFY → LOAD MINIMAL → APPLY RULES → EXECUTE → VALIDATE
```

Never invent patterns. Every decision must be traceable.

---

# 📚 Mandatory References by Task Type

## REST Endpoint Task

* `.agents/rules/coding-standards.md`
* `.agents/rules/architecture-guidelines.md`
* `.agents/knowledge/architecture/microservices.md`
* `.agents/skills/crud/novo-servico-crud.md`

---

## Security Task

* `.agents/rules/security-rules.md`
* `.agents/knowledge/spring/security.md`
* `.agents/skills/security/aplicar-rbac.md`

---

## Kafka Task

* `.agents/knowledge/spring/kafka.md`
* `.agents/skills/kafka/publicar-evento.md`
* `.agents/skills/kafka/consumir-evento.md`

---

## MongoDB Task

* `.agents/knowledge/spring/data-mongodb.md`
* `.agents/skills/mongo/montar-query-dinamica.md`

---

## Order Domain Task

* `.agents/knowledge/orderflow/pedidos.md`
* `.agents/skills/pedidos/criar-pedido.md`

---

## Error Handling Task

* `.agents/rules/error-handling.md`
* `.agents/knowledge/java/exception-handling.md`

---

## Infra Task

* `.agents/knowledge/infra/docker.md`

---

# 🚫 No Assumptions Rule

If any required information is missing:

* DO NOT guess
* DO NOT invent

You MUST ask or search knowledge.

---

# 🔄 Knowledge Evolution Rule

If you detect:

* repeated logic
* missing documentation
* inconsistencies

You MUST:

1. update `.agents/knowledge/`
2. or create new file

---

# 🧩 Skill Creation Rule

If a task is reusable:

* create a skill in `.agents/skills/`
* include examples and validations

---

# 🔍 Decision Transparency Rule

Before coding, state:

* which knowledge was used
* which rules applied
* which skill used

---

# 🧪 Testing Rule

When applicable:

* generate unit tests
* validate business rules
* cover edge cases

---

# 🔥 Non-Negotiable Rules

## Architecture

* Gateway is the ONLY entry point
* No direct service-to-service HTTP
* Kafka is the main communication

---

## MongoDB

* Use MongoTemplate for dynamic queries
* Never use deleteById without soft delete

---

## Kafka

* Always log before send and after receive
* Never rethrow in consumers

---

## Security

* JWT uses "roles"
* No ROLE_ prefix
* Gateway uses WebFlux Security

---

## Code Quality

* Use SLF4J only
* Constructor injection always
* Validate all inputs

---

# 🚫 Anti-Patterns

* System.out.println
* Hardcoded strings
* Duplicate logic
* Ignoring `.agents/`

---

# 🏁 Final Objective

Transform OrderFlow into:

* scalable
* consistent
* secure
* maintainable

---

# 🔥 Final Rule

If in doubt:

👉 load minimal `.agents/` and follow it strictly.
