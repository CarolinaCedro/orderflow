# Skill: Create a New CRUD Microservice

## Purpose

Create a complete, production-ready CRUD microservice following OrderFlow's
AbstractController/AbstractService template method pattern.

## When to Use

- Adding a new domain concept to OrderFlow (e.g., `supplier-service`, `shipping-service`).
- Creating a service that needs REST endpoints + MongoDB persistence + JWT security.

## Prerequisites

- Root `pom.xml` has a `<modules>` section.
- `order-model`, `order-rest-service`, `order-utils` are built and in local Maven repository.
- Eureka server is running or will be started alongside the new service.

## Knowledge References

- `.agents/rules/architecture-guidelines.md` — service checklist, shared lib rules
- `.agents/rules/coding-standards.md` — package structure, @PreAuthorize, constructor injection
- `.agents/knowledge/spring/boot.md` — autoconfiguration, library vs service POM
- `.agents/knowledge/spring/security.md` — MicroserviceSecurityConfig via order-utils

---

## Steps

### Step 1: Create Maven Module

Add to root `pom.xml`:
```xml
<modules>
    <!-- existing modules -->
    <module>supplier-service</module>
</modules>
```

Create `supplier-service/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.cedro</groupId>
        <artifactId>orderflow</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>supplier-service</artifactId>
    <name>supplier-service</name>

    <dependencies>
        <!-- Shared libraries -->
        <dependency>
            <groupId>org.cedro</groupId>
            <artifactId>order-model</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.cedro</groupId>
            <artifactId>order-rest-service</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.cedro</groupId>
            <artifactId>order-utils</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
        </dependency>

        <!-- Spring Cloud (Eureka) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals><goal>repackage</goal></goals>
                    </execution>
                </executions>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 2: Create Entity in order-model

Add `Supplier.java` to `order-model/src/main/java/org/cedro/ordermodel/model/`:

```java
package org.cedro.ordermodel.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "suppliers")
public class Supplier {

    @Id
    private String id;

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "cnpj is required")
    private String cnpj;

    @Email(message = "email must be valid")
    private String email;

    @NotBlank(message = "phone is required")
    private String phone;

    private Boolean active = true;

    private Metadata metadata;
}
```

Rebuild `order-model`: `mvn clean install -DskipTests -pl order-model`

### Step 3: Create Application Entry Point

```
supplier-service/src/main/java/org/cedro/supplierservice/
    SupplierServiceApplication.java
    controller/SupplierController.java
    service/SupplierService.java
    service/impl/SupplierServiceImpl.java
    repository/SupplierRepository.java
```

`SupplierServiceApplication.java`:
```java
package org.cedro.supplierservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SupplierServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SupplierServiceApplication.class, args);
    }
}
```

### Step 4: Create Repository

```java
package org.cedro.supplierservice.repository;

import org.cedro.ordermodel.model.Supplier;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SupplierRepository extends MongoRepository<Supplier, String> {
    Optional<Supplier> findByCnpj(String cnpj);
}
```

### Step 5: Create Service Interface

```java
package org.cedro.supplierservice.service;

import com.cedro.orderrestservice.rest.service.impl.AbstractService;
import org.cedro.ordermodel.model.Supplier;

public interface SupplierService {
    // Add custom methods beyond CRUD here
}
```

### Step 6: Create Service Implementation

```java
package org.cedro.supplierservice.service.impl;

import com.cedro.orderrestservice.rest.service.impl.AbstractService;
import org.cedro.ordermodel.model.Supplier;
import org.cedro.supplierservice.repository.SupplierRepository;
import org.cedro.supplierservice.service.SupplierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;

@Service
public class SupplierServiceImpl extends AbstractService<Supplier> implements SupplierService {

    private static final Logger log = LoggerFactory.getLogger(SupplierServiceImpl.class);

    private final SupplierRepository supplierRepository;

    public SupplierServiceImpl(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Override
    protected MongoRepository<Supplier, String> getRepository() {
        return supplierRepository;
    }

    @Override
    protected Class<Supplier> getEntityClass() {
        return Supplier.class;
    }
}
```

### Step 7: Create Controller with @PreAuthorize on All Methods

```java
package org.cedro.supplierservice.controller;

import com.cedro.orderrestservice.rest.controller.AbstractController;
import com.cedro.orderrestservice.rest.service.impl.AbstractService;
import org.cedro.ordermodel.model.Supplier;
import org.cedro.supplierservice.service.impl.SupplierServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orderflow/v1/supplier")
public class SupplierController extends AbstractController<Supplier> {

    private final SupplierServiceImpl supplierService;

    public SupplierController(SupplierServiceImpl supplierService) {
        this.supplierService = supplierService;
    }

    @Override
    protected AbstractService<Supplier> getService() {
        return supplierService;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<Supplier> save(Supplier value, String returnEntity) {
        return super.save(value, returnEntity);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<Supplier> update(String id, Supplier model) {
        return super.update(id, model);
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void deleteById(String id) {
        super.deleteById(id);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<Supplier> findById(String id) {
        return super.findById(id);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<List<Supplier>> list(Map<String, String> allRequestParams) {
        return super.list(allRequestParams);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<Long> count(Map<String, String> allRequestParams) {
        return super.count(allRequestParams);
    }
}
```

### Step 8: Create application.yml

```yaml
spring:
  application:
    name: supplier-service

  config:
    import: optional:configserver:http://localhost:8888

  data:
    mongodb:
      uri: mongodb://root:products@localhost:27018/orderflow?authSource=admin

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:9999/.well-known/jwks.json

eureka:
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: http://localhost:8761/eureka/

server:
  port: 8087    # Choose an available port

logging:
  level:
    org.apache.kafka: WARN
```

### Step 9: Build and Verify

```bash
# From project root
mvn clean install -DskipTests -pl order-model,supplier-service

# Start service (after Eureka and order-security-server are running)
java -jar supplier-service/target/supplier-service-0.0.1-SNAPSHOT.jar

# Test
curl -X POST http://localhost:8080/supplier-service/orderflow/v1/supplier \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"ACME Ltda","cnpj":"12.345.678/0001-90","email":"contact@acme.com","phone":"11999999999"}'
```

---

## Validation Checklist

- [ ] Module added to root `pom.xml` `<modules>`
- [ ] Entity added to `order-model` (not in the service module)
- [ ] Service extends `AbstractService<T>` and implements `getEntityClass()`
- [ ] Controller extends `AbstractController<T>` and overrides ALL 6 methods with `@PreAuthorize`
- [ ] `application.yml` has `spring.application.name`, MongoDB URI, Eureka config, JWKS URI
- [ ] Port is unique (not conflicting with other services — see port table in CLAUDE.md)
- [ ] No `SecurityFilterChain` bean declared (uses autoconfigured `MicroserviceSecurityConfig`)
- [ ] `spring.config.import: optional:configserver:...` present

## Common Mistakes

- Defining the entity in the service module — it belongs in `order-model`.
- Forgetting `getEntityClass()` — compile error.
- Not overriding ALL 6 controller methods — some methods will be unprotected.
- Adding `spring-boot-maven-plugin` `repackage` to a library module.
- Using the same port as another service — `Address already in use` at startup.
- Missing JWKS URI — service starts but every request returns 401 (cannot validate tokens).
