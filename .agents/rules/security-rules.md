# Security Rules — OrderFlow

Security is enforced at two layers (gateway + service) with JWT RS256. These rules define the
exact configuration required at each layer.

---

## JWT Claim for Roles

The JWT `"roles"` claim is a JSON array of role name strings:

```json
{
  "sub": "admin",
  "roles": ["ADMIN"],
  "iat": 1742389200,
  "exp": 1742392800
}
```

Critical rules:
- The claim name is `"roles"` — NOT `"scope"`, NOT `"authorities"`, NOT `"role"`.
- Role values are plain strings: `"ADMIN"`, `"MANAGER"`, `"BUYER"`, `"VIEWER"`.
- There is NO `ROLE_` prefix in the claim values.
- `@PreAuthorize` expressions use `hasAuthority("ADMIN")`, not `hasRole("ADMIN")`.
  (`hasRole` automatically adds a `ROLE_` prefix — never use it in this project.)

---

## JwtGrantedAuthoritiesConverter Configuration

This is the exact configuration in `MicroserviceSecurityConfig` (order-utils). Never deviate:

```java
@Bean
@ConditionalOnMissingBean(JwtAuthenticationConverter.class)
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");  // reads "roles" claim
    grantedAuthoritiesConverter.setAuthorityPrefix("");             // no ROLE_ prefix

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return converter;
}
```

If you override this bean in a microservice, you MUST replicate these two settings exactly.

---

## RBAC Matrix

| Operation             | ADMIN | MANAGER | BUYER | VIEWER |
|-----------------------|-------|---------|-------|--------|
| Save (POST)           | YES   | YES     | NO    | NO     |
| Update (PUT)          | YES   | YES     | NO    | NO     |
| Delete (soft)         | YES   | NO      | NO    | NO     |
| Find by ID (GET /{id})| YES   | YES     | YES   | YES    |
| List (GET /)          | YES   | YES     | YES   | YES    |
| Count (GET /count)    | YES   | YES     | YES   | YES    |

`@PreAuthorize` expressions by operation:
```java
// Save
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")

// Update
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")

// Delete (soft)
@PreAuthorize("hasAuthority('ADMIN')")

// Read (findById, list, count)
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
```

For Order creation (BUYER can create orders):
```java
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER')")
public ResponseEntity<Order> save(Order value, String returnEntity) { ... }
```

---

## order-security-server — NOT a Resource Server

`order-security-server` issues JWT tokens. It does NOT validate them.

Its `SecurityConfig`:
- Uses `@EnableWebSecurity` + `HttpSecurity` (standard MVC security).
- Does NOT configure `.oauth2ResourceServer(...)`.
- Permits `/auth/login` and `/.well-known/**` publicly.
- Has its own `PasswordEncoder` bean (`BCryptPasswordEncoder`).
- Does NOT depend on `order-utils` autoconfiguration.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login", "/.well-known/**", "/actuator/**",
                                 "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

Never add `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` to `order-security-server`.

---

## Gateway Security (WebFlux)

The gateway is reactive. Its security config MUST use:
- `@EnableWebFluxSecurity` — NOT `@EnableWebSecurity`
- `ServerHttpSecurity` — NOT `HttpSecurity`
- `SecurityWebFilterChain` return type — NOT `SecurityFilterChain`

```java
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(auth -> auth
                .pathMatchers("/order-security-server/**", "/actuator/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

The gateway JWKS URI is configured in `application.yml`:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:9999/.well-known/jwks.json
```

---

## Microservice Security (MicroserviceSecurityConfig autoconfiguration)

Every microservice gets its security for free via `order-utils` autoconfiguration.

The autoconfiguration file:
```
order-utils/src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
Content:
```
org.cedro.orderutils.security.MicroserviceSecurityConfig
```

This means:
1. Any service with `order-utils` on the classpath gets `MicroserviceSecurityConfig` loaded.
2. The `SecurityFilterChain` bean is `@ConditionalOnMissingBean` — declare your own to override.
3. The `JwtAuthenticationConverter` bean is also `@ConditionalOnMissingBean` — override if needed.
4. Each service must configure its JWKS URI in `application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:9999/.well-known/jwks.json
```

---

## Public Endpoints

The following endpoints are ALWAYS permitted without authentication:

- `/auth/login` — login endpoint in `order-security-server`
- `/.well-known/jwks.json` — public key endpoint in `order-security-server`
- `/actuator/**` — health checks (all services)
- `/swagger-ui/**` — API docs (all services)
- `/v3/api-docs/**` — OpenAPI spec (all services)

At the gateway, `order-security-server/**` is permitted to allow unauthenticated login:
```yaml
# Gateway routes all /order-security-server/** to order-security-server:9999
```

---

## RSA Key Management

`KeyConfig` in `order-security-server` generates a 2048-bit RSA key pair in memory at startup:

```java
@Bean
public RSAKey rsaKey() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair pair = gen.generateKeyPair();
    return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
        .privateKey((RSAPrivateKey) pair.getPrivate())
        .keyID("main-key")
        .build();
}
```

Implications:
- Every restart of `order-security-server` generates a new key pair.
- All existing tokens become invalid after a restart (in-memory key is lost).
- This is acceptable for development.
- For production: externalize the private key to a file, Vault, or AWS Secrets Manager.
  Replace `KeyPairGenerator` with loading from a PEM/PKCS8 file.

Never commit private keys to git.

---

## Password Storage

All passwords MUST be BCrypt encoded. `DataInitializer` in `order-security-server` demonstrates
the correct pattern:

```java
user.setPassword(passwordEncoder.encode(rawPassword));
```

Never store or log raw passwords. Never compare passwords with `==` or `.equals()`.
Always use `passwordEncoder.matches(rawPassword, encodedPassword)`.

---

## Default Users (DataInitializer)

Seeded at startup in `order-security-server`:

| Username  | Password    | Role    |
|-----------|-------------|---------|
| admin     | admin123    | ADMIN   |
| manager1  | manager123  | MANAGER |
| buyer1    | buyer123    | BUYER   |

These are dev credentials only. Externalize for production via environment variables.
