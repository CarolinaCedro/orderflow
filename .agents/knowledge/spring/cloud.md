# Spring Cloud 2024.0.1 — OrderFlow Reference

Spring Cloud components: Eureka (discovery), Gateway (routing), Config Server, OpenFeign.

---

## Spring Cloud BOM

Managed in the root `pom.xml`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2024.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

This manages versions for all Spring Cloud starters. Never specify individual Spring Cloud
dependency versions — the BOM handles them.

---

## Eureka Server

Standalone service at `eureka-server:8761`.

```yaml
# eureka-server/application.yml
spring:
  application:
    name: eureka-server
eureka:
  client:
    register-with-eureka: false  # Does not register itself
    fetch-registry: false        # Does not fetch registry
server:
  port: 8761
```

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

Access Eureka dashboard: `http://localhost:8761`

---

## Eureka Client Registration

Every microservice (except `eureka-server`) registers:

```yaml
eureka:
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

Dependency:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

`@EnableEurekaClient` is NOT needed in Spring Boot 3 — autoconfigured by the starter.

After startup, verify registration at: `http://localhost:8761` — the service name should appear.

---

## Spring Cloud Gateway

Gateway at `gateway-server:8080`. Routes to services discovered via Eureka.

```yaml
# gateway-server/application.yml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true  # critical — service IDs in Eureka are uppercase by default
```

With `lower-case-service-id: true`, the route pattern becomes:

```
http://localhost:8080/{service-name}/{original-path}
```

Examples:
- `GET http://localhost:8080/order-service/orderflow/v1/order` → routes to `order-service:8081/orderflow/v1/order`
- `GET http://localhost:8080/inventory-service/orderflow/v1/product` → routes to `inventory-service:8089/orderflow/v1/product`
- `POST http://localhost:8080/order-security-server/auth/login` → routes to `order-security-server:9999/auth/login`

Service name in the URL must match `spring.application.name` in lowercase with hyphens.

Gateway dependency:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
```

`spring-boot-starter-web` must NOT be present — gateway uses `spring-boot-starter-webflux`.

---

## Adding Explicit Routes

When automatic discovery routing is not sufficient:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service-route
          uri: lb://order-service      # lb:// means load-balanced via Eureka
          predicates:
            - Path=/orders/**
          filters:
            - StripPrefix=1            # Remove /orders prefix before forwarding
```

Prefer auto-discovery (`lower-case-service-id: true`) over explicit routes when possible.

---

## Config Server

Standalone service at `config-server:8888`.

```yaml
# config-server/application.yml
spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/CarolinaCedro/orderflow-config
          search-paths: config
          clone-on-start: true
          default-label: master
server:
  port: 8888
```

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication { ... }
```

### Consuming Config Server (Optional Import)

Services import config optionally to avoid startup failures when config server is down:

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

The `optional:` prefix is critical. Without it, the service fails to start if config server
is unreachable.

Config server is currently used by `order-service`. Properties defined in the git repo's
config directory override local `application.yml` properties.

---

## OpenFeign (ViaCEP)

Feign is used in `order-utils` for external address lookup:

```java
// order-utils: org.cedro.orderutils.feign.viacep.service.ViaCep
@FeignClient(name = "viacep", url = "https://viacep.com.br/ws")
public interface ViaCep {

    @GetMapping("/{cep}/json/")
    Endereco findByCep(@PathVariable("cep") String cep);
}
```

```java
// Response record
public record Endereco(
    String cep,
    String logradouro,
    String bairro,
    String localidade,
    String uf
) {}
```

Feign dependency:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

Enable Feign in the consuming service:
```java
@SpringBootApplication
@EnableFeignClients(basePackages = "org.cedro.orderutils.feign")
public class OrderServiceApplication { ... }
```

Or scan all Feign clients from the classpath:
```java
@EnableFeignClients(basePackages = {"org.cedro.orderutils", "org.cedro.orderservice"})
```

Rules:
- Only `ViaCep` in `order-utils` is an approved Feign client.
- New external API clients go in `order-utils`, not in individual services.
- Never use Feign for service-to-service calls — use Kafka for async communication.

---

## Service Startup Dependencies

Services must start in this order (soft dependency via Eureka registration readiness):

```
1. eureka-server:8761
2. config-server:8888    (optional — services use optional: import)
3. order-security-server:9999   (must be up for JWT JWKS endpoint)
4. order-service:8081
   payment-service:8085
   notification-service:8083
   inventory-service:8089
5. gateway-server:8080   (discovers services from Eureka — start last)
```

In development, it's safe to start all together — services retry Eureka registration and
the JWKS endpoint fetch automatically.
