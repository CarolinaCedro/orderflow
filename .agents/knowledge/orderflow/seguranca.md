# Security Deep-Dive — OrderFlow

Complete reference for `order-security-server` and the security architecture across all services.

---

## order-security-server Architecture

`order-security-server:9999` is the Identity Provider (IdP) for OrderFlow.

Responsibilities:
1. Authenticate users (username + BCrypt password).
2. Issue JWT RS256 tokens with `"roles"` claim.
3. Expose the RSA public key via JWKS endpoint for other services to validate tokens.

NOT its responsibility:
- Validating tokens (it issues them — it is not a resource server).
- Protecting resources (it has no business resources — only auth endpoints).

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

Key facts:
- 2048-bit RSA key pair, generated in memory at startup.
- `keyID("main-key")` is embedded in the JWT header (`kid` claim).
- The `kid` is used by validators to look up the correct key in the JWK Set.
- **Key rotation**: restarting `order-security-server` generates a new key pair, invalidating
  all existing tokens. For production, persist the key and reload on restart.

---

## Token Generation (TokenService)

```java
@Service
public class TokenService {

    private final RSAKey rsaKey;

    @Value("${security.jwt.expiration:3600}")
    private long expiration;  // Seconds; default 3600 = 1 hour

    public TokenService(RSAKey rsaKey) {
        this.rsaKey = rsaKey;
    }

    public String generateToken(AppUser user) {
        try {
            RSASSASigner signer = new RSASSASigner(rsaKey);

            // Extract role enum names as strings
            List<String> roles = user.getRoles().stream()
                .map(Enum::name)
                .toList();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getUsername())      // "sub" claim = username
                .claim("roles", roles)            // Custom "roles" claim = ["ADMIN"]
                .issueTime(new Date())            // "iat" claim
                .expirationTime(new Date(System.currentTimeMillis() + expiration * 1000)) // "exp"
                .build();

            SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKey.getKeyID())     // "kid" in header = "main-key"
                    .build(),
                claims
            );

            jwt.sign(signer);
            return jwt.serialize();  // Base64url encoded token string
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT", e);
        }
    }
}
```

Resulting JWT header:
```json
{"alg": "RS256", "kid": "main-key"}
```

Resulting JWT payload:
```json
{
  "sub": "admin",
  "roles": ["ADMIN"],
  "iat": 1742389200,
  "exp": 1742392800
}
```

---

## JWKS Endpoint (JwksController)

```java
@RestController
public class JwksController {

    private final RSAKey rsaKey;

    public JwksController(RSAKey rsaKey) {
        this.rsaKey = rsaKey;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        JWKSet jwkSet = new JWKSet(rsaKey.toPublicJWK());  // toPublicJWK() = no private key
        return jwkSet.toJSONObject();
    }
}
```

Response example:
```json
{
  "keys": [{
    "kty": "RSA",
    "kid": "main-key",
    "n": "<base64url-modulus>",
    "e": "AQAB"
  }]
}
```

This endpoint is consumed by:
- `gateway-server` — configured via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
- All microservices — same configuration

---

## AuthController — Login Flow

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

Step-by-step:
1. `findByUsername(request.username())` → `Optional<AppUser>` (MongoDB query).
2. `.filter(user -> user.isActive() && passwordEncoder.matches(...))` — BCrypt comparison.
3. If match → generate token → return `200 OK` with `{accessToken, expiresIn}`.
4. If no match → return `401 Unauthorized` (empty body).

DTO types:
```java
public record LoginRequest(String username, String password) {}
public record TokenResponse(String accessToken, Long expiresIn) {}
```

---

## DataInitializer — Seeded Users

```java
@Component
public class DataInitializer implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent("admin",    "admin123",   Set.of(Role.ADMIN));
        createIfAbsent("manager1", "manager123", Set.of(Role.MANAGER));
        createIfAbsent("buyer1",   "buyer123",   Set.of(Role.BUYER));
    }
}
```

`createIfAbsent` is idempotent — checks `findByUsername` before inserting.
Passwords stored as BCrypt hashes.

---

## RBAC Matrix

| Role    | Save | Update | Delete | Read |
|---------|------|--------|--------|------|
| ADMIN   | YES  | YES    | YES    | YES  |
| MANAGER | YES  | YES    | NO     | YES  |
| BUYER   | Depends on resource | NO | NO | YES |
| VIEWER  | NO   | NO     | NO     | YES  |

For orders, BUYER can create:
```java
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER')")
public ResponseEntity<Order> save(Order value, String returnEntity)
```

For products, only ADMIN/MANAGER can create:
```java
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
public ResponseEntity<Product> save(Product value, String returnEntity)
```

---

## How Gateway Validates Tokens

1. Client sends `Authorization: Bearer <token>` to `gateway:8080`.
2. `GatewaySecurityConfig` configures `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`.
3. Spring Security's reactive JWT processor fetches JWKS from `localhost:9999/.well-known/jwks.json`
   (cached — not fetched on every request, refreshed periodically or on `kid` mismatch).
4. JWT signature is verified using the RSA public key matching `kid: "main-key"`.
5. Token expiration is validated.
6. If valid: request is forwarded with the JWT in the `Authorization` header.
7. If invalid: `401 Unauthorized` is returned immediately by the gateway.

---

## How Microservices Validate Tokens (MicroserviceSecurityConfig)

Each microservice independently validates the JWT:
1. `MicroserviceSecurityConfig` (autoconfigured from `order-utils`) configures
   `.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(...)))`.
2. The JWKS URI is set via `application.yml`:
   ```yaml
   spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://localhost:9999/.well-known/jwks.json
   ```
3. JWT is validated identically to the gateway.
4. `JwtAuthenticationConverter` extracts `"roles"` claim → creates `GrantedAuthority` objects.
5. `@PreAuthorize` SpEL expressions evaluate against these authorities.

---

## SecurityConfig in order-security-server

Does NOT configure OAuth2 resource server (this is NOT a resource server):

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
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

No `.oauth2ResourceServer(...)` — this server issues tokens, never validates them for resources.
