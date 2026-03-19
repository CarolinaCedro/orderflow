# Spring Security 6 — OrderFlow Reference

JWT RS256 security configuration across all layers of OrderFlow.

---

## JWT RS256 Flow

```
Client
  │
  ├── POST /order-security-server/auth/login  {username, password}
  │       ↓
  │   order-security-server:9999
  │       ↓ BCrypt verify password
  │       ↓ Generate JWT (RS256, RSA private key, "roles" claim)
  │       ↓ Return {accessToken, expiresIn}
  │
  ├── GET /order-service/orderflow/v1/order
  │   Authorization: Bearer <JWT>
  │       ↓
  │   gateway-server:8080
  │       ↓ Fetch JWKS from http://localhost:9999/.well-known/jwks.json
  │       ↓ Validate JWT signature with RSA public key
  │       ↓ Route request to order-service:8081
  │       ↓
  │   order-service:8081
  │       ↓ Validate JWT again (MicroserviceSecurityConfig)
  │       ↓ Extract roles from "roles" claim
  │       ↓ Evaluate @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
  │       ↓ Return response
```

---

## Token Generation (order-security-server)

`TokenService` uses Nimbus JOSE + JWT:

```java
@Service
public class TokenService {

    private final RSAKey rsaKey;

    @Value("${security.jwt.expiration:3600}")
    private long expiration;

    public TokenService(RSAKey rsaKey) {
        this.rsaKey = rsaKey;
    }

    public String generateToken(AppUser user) {
        try {
            RSASSASigner signer = new RSASSASigner(rsaKey);

            List<String> roles = user.getRoles().stream()
                .map(Enum::name)
                .toList();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .claim("roles", roles)           // "roles" — not "scope" or "authorities"
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + expiration * 1000))
                .build();

            SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims
            );

            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT", e);
        }
    }
}
```

---

## JWKS Endpoint (order-security-server)

Exposes the RSA public key in JWK Set format for token validation by other services:

```java
@RestController
public class JwksController {

    private final RSAKey rsaKey;

    public JwksController(RSAKey rsaKey) {
        this.rsaKey = rsaKey;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        JWKSet jwkSet = new JWKSet(rsaKey.toPublicJWK());  // Only public key exposed
        return jwkSet.toJSONObject();
    }
}
```

`rsaKey.toPublicJWK()` strips the private key. Only the public key is ever exposed.

---

## RSA Key Generation (KeyConfig)

```java
@Configuration
public class KeyConfig {

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
}
```

Key ID `"main-key"` is embedded in the JWT header. The gateway uses it to locate the correct
key in the JWK Set when validating tokens.

---

## Microservice Security (MicroserviceSecurityConfig)

Autoconfigured via `order-utils`. See `.agents/knowledge/spring/boot.md` for autoconfiguration
details. Provides:

1. Stateless session (no HTTP session — JWT only).
2. CSRF disabled (stateless APIs do not need CSRF protection).
3. JWT resource server with `JwtAuthenticationConverter` using `"roles"` claim.
4. Public endpoints: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`.
5. All other requests require authentication.
6. `@EnableMethodSecurity` — activates `@PreAuthorize`.

---

## JwtAuthenticationConverter (microservices)

```java
@Bean
@ConditionalOnMissingBean(JwtAuthenticationConverter.class)
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");  // reads JWT "roles" array
    grantedAuthoritiesConverter.setAuthorityPrefix("");             // no ROLE_ prefix added

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return converter;
}
```

Without `setAuthorityPrefix("")`, Spring would add `ROLE_` prefix, making role `"ADMIN"` become
`ROLE_ADMIN` in `GrantedAuthority`, breaking `hasAuthority("ADMIN")` expressions.

---

## ReactiveJwtAuthenticationConverter (gateway)

The gateway uses WebFlux. When you need a custom JWT converter at the gateway level:

```java
@Bean
public ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
    authoritiesConverter.setAuthoritiesClaimName("roles");
    authoritiesConverter.setAuthorityPrefix("");

    ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> Flux.fromIterable(authoritiesConverter.convert(jwt))
    );
    return converter;
}
```

Use this in `GatewaySecurityConfig` if you need role-based authorization at the gateway level.
Currently the gateway only validates JWT signature — role evaluation happens at the service.

---

## Gateway Security Config

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

The `Customizer.withDefaults()` for JWT uses the `jwk-set-uri` from `application.yml`:
```yaml
spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://localhost:9999/.well-known/jwks.json
```

---

## @PreAuthorize Expressions

`@EnableMethodSecurity` (enabled by `MicroserviceSecurityConfig`) activates SpEL-based
method security.

Valid expressions:
```java
// Single role
@PreAuthorize("hasAuthority('ADMIN')")

// Multiple roles (OR)
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")

// Dynamic check (current user must match or be admin)
@PreAuthorize("hasAuthority('ADMIN') or #username == authentication.name")

// Check claim value directly
@PreAuthorize("hasAuthority('BUYER') and #order.customerId == authentication.name")
```

NEVER use `hasRole(...)` in this project — it prepends `ROLE_` which breaks the matching.

---

## SecurityContextHolder Usage

To get the current authenticated user's username in a service:

```java
// In order-utils: SecurityContextHelper
public static String getCurrentUsername() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .map(Authentication::getName)
        .orElse("system");
}

// Usage in service layer:
String updatedBy = SecurityContextHelper.getCurrentUsername();
order.getMetadata().setUpdatedBy(updatedBy);
```

---

## order-security-server SecurityConfig

Does NOT configure OAuth2 resource server:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login", "/.well-known/**", "/actuator/**",
                                 "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            );
        // NO .oauth2ResourceServer() — this is the issuer, not a resource server
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

---

## Login Flow (AuthController)

```java
@PostMapping("/login")
public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
    return appUserRepository.findByUsername(request.username())
        .filter(user -> user.isActive()
                && passwordEncoder.matches(request.password(), user.getPassword()))
        .map(user -> ResponseEntity.ok(
            new TokenResponse(tokenService.generateToken(user), 3600L)))
        .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
}
```

Steps:
1. Find user by username (returns `Optional<AppUser>`).
2. Filter: user must be active AND password must match (BCrypt).
3. If match: generate token, return 200 with `TokenResponse`.
4. If no match: return 401.

No dedicated `AuthenticationManager` or `UserDetailsService` needed — manual validation pattern.
