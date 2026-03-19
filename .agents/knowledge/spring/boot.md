# Spring Boot 3.4.3 — OrderFlow Reference

Spring Boot 3.4.3 with Spring Cloud 2024.0.1. Key behaviors and configurations used in this project.

---

## Autoconfiguration in order-utils

`order-utils` is a shared library that autoconfigures `MicroserviceSecurityConfig` for all
consuming microservices. This uses the standard Spring Boot autoconfiguration mechanism.

### The autoconfiguration file

```
order-utils/src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Content:
```
org.cedro.orderutils.security.MicroserviceSecurityConfig
```

### How it works

When a microservice depends on `order-utils`:
1. Spring Boot scans `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
   in all JARs on the classpath.
2. It finds `MicroserviceSecurityConfig` and processes it as an autoconfiguration class.
3. `@AutoConfiguration` marks the class as an autoconfiguration candidate.
4. `@ConditionalOnMissingBean(SecurityFilterChain.class)` ensures the default `SecurityFilterChain`
   is only created if no custom one exists.
5. `@ConditionalOnMissingBean(JwtAuthenticationConverter.class)` same logic for the JWT converter.

### The class itself

```java
@AutoConfiguration
@EnableWebSecurity
@EnableMethodSecurity  // Required for @PreAuthorize to work
public class MicroserviceSecurityConfig {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
            );
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(JwtAuthenticationConverter.class)
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }
}
```

---

## Library vs Executable Module (POM Differences)

### Executable service module (has `spring-boot-maven-plugin` with `repackage`)

```xml
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
        </plugin>
    </plugins>
</build>
```

### Library module (NO `repackage` — produces a regular JAR consumable by other modules)

`order-model`, `order-rest-service`, `order-utils` do NOT use `spring-boot-maven-plugin`'s
`repackage` goal. They produce plain JARs that other modules depend on.

If you accidentally add `repackage` to a library module, the JAR becomes a fat JAR that cannot
be used as a Maven dependency by other services.

Identifying which type a module is:
- Library: no `@SpringBootApplication`, no `main()` method, no `repackage`.
- Service: has `@SpringBootApplication`, has `main()` method, has `repackage`.

---

## application.yml Structure

Standard structure for an OrderFlow microservice:

```yaml
spring:
  application:
    name: order-service          # Required for Eureka registration and config server

  config:
    import: optional:configserver:http://localhost:8888  # Optional config server

  data:
    mongodb:
      uri: mongodb://root:products@localhost:27018/orderflow?authSource=admin

  kafka:
    bootstrap-servers: localhost:9091
    consumer:
      group-id: order-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

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
  port: 8081

logging:
  level:
    org.springframework.data.mongodb.core.MongoTemplate: DEBUG  # Enable only in dev
    org.apache.kafka: WARN   # Suppress Kafka noise
```

---

## @ConditionalOnMissingBean Pattern

Used extensively in `order-utils` to allow overriding:

```java
// In order-utils autoconfiguration — provides default behavior
@Bean
@ConditionalOnMissingBean(SecurityFilterChain.class)
public SecurityFilterChain filterChain(HttpSecurity http, ...) throws Exception { ... }

// In a microservice that needs custom security — overrides the autoconfigured bean
@Configuration
public class CustomSecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Custom config — order-utils bean is suppressed because this one exists
        ...
    }
}
```

Other conditional annotations:
- `@ConditionalOnProperty` — activate bean based on a property value.
- `@ConditionalOnClass` — activate bean if a class is on the classpath.
- `@ConditionalOnBean` — activate bean if another bean exists.

---

## Spring Boot Profiles

OrderFlow uses `application.yml` profiles:

```
application.yml           → defaults (dev-friendly)
application-prod.yml      → production overrides (externalize secrets, disable debug)
application-qa.yml        → QA environment overrides
application-test.yml      → test overrides (H2 in some older modules — check each service)
```

Activate a profile:
```
java -jar order-service.jar --spring.profiles.active=prod
```

Or in `application.yml`:
```yaml
spring:
  profiles:
    active: prod
```

---

## Actuator

All services include `spring-boot-starter-actuator`. Endpoints are permitted without auth:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

Access: `http://localhost:{port}/actuator/health`

---

## ApplicationRunner for Data Seeding

`DataInitializer` in `order-security-server` implements `ApplicationRunner`:

```java
@Component
public class DataInitializer implements ApplicationRunner {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AppUserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent("admin", "admin123", Set.of(Role.ADMIN));
        createIfAbsent("manager1", "manager123", Set.of(Role.MANAGER));
        createIfAbsent("buyer1", "buyer123", Set.of(Role.BUYER));
    }

    private void createIfAbsent(String username, String rawPassword, Set<Role> roles) {
        if (repository.findByUsername(username).isEmpty()) {
            AppUser user = new AppUser();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRoles(roles);
            repository.save(user);
        }
    }
}
```

`createIfAbsent` prevents duplicate seeding on every restart. This is idempotent.
