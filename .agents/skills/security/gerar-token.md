# Skill: Generate JWT Token (order-security-server)

## Purpose

Understand and correctly implement or modify JWT generation in `order-security-server` using
Nimbus JOSE + JWT with RSA RS256 signing.

## When to Use

- Modifying token payload (adding claims, changing expiration).
- Adding token refresh endpoint.
- Changing RSA key configuration (memory → file → Vault).
- Debugging authentication failures traced to token content.

## Prerequisites

- `order-security-server` is running.
- `KeyConfig` bean provides an `RSAKey`.
- `AppUser` entity with `username`, `password`, `roles`, `active` fields exists.

## Knowledge References

- `.agents/knowledge/orderflow/seguranca.md` — full security server architecture
- `.agents/knowledge/spring/security.md` — JWT RS256 flow, JWKS endpoint
- `.agents/rules/security-rules.md` — JWT claim for roles must be "roles"

---

## Steps

### Step 1: Understand the RSA Key Setup

`KeyConfig` generates a 2048-bit RSA key pair at startup:

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
            .keyID("main-key")   // Must match across all references
            .build();
    }
}
```

The `RSAKey` bean holds BOTH the public and private key. `JwksController` exposes only the
public part via `rsaKey.toPublicJWK()`.

### Step 2: Verify TokenService Generates Correct Claims

The critical contract: claim name MUST be `"roles"`, values MUST be plain strings (no prefix):

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
                .map(Enum::name)          // Role.ADMIN → "ADMIN"
                .toList();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getUsername())       // "sub": "admin"
                .claim("roles", roles)             // "roles": ["ADMIN"]  ← critical
                .issueTime(new Date())             // "iat": current time
                .expirationTime(new Date(
                    System.currentTimeMillis() + expiration * 1000))  // "exp"
                .build();

            SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKey.getKeyID())      // "kid": "main-key"
                    .build(),
                claims
            );

            jwt.sign(signer);
            return jwt.serialize();   // Returns: xxxxx.yyyyy.zzzzz
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT", e);
        }
    }
}
```

### Step 3: Add Custom Claims (Tenant, Email, etc.)

```java
JWTClaimsSet claims = new JWTClaimsSet.Builder()
    .subject(user.getUsername())
    .claim("roles", roles)
    .claim("tenantId", user.getTenantId())       // Custom claim
    .claim("email", user.getEmail())             // Custom claim
    .issueTime(new Date())
    .expirationTime(new Date(System.currentTimeMillis() + expiration * 1000))
    .build();
```

Consuming the custom claim in a microservice:
```java
// In a service method, extract from SecurityContext
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
if (auth instanceof JwtAuthenticationToken jwtAuth) {
    String tenantId = (String) jwtAuth.getTokenAttributes().get("tenantId");
}
```

### Step 4: Validate the Generated Token

Decode manually to verify claims (use jwt.io or CLI):

```bash
# Obtain token
TOKEN=$(curl -s -X POST http://localhost:9999/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.accessToken')

# Decode payload (no signature verification — dev only)
echo $TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .
```

Expected decoded payload:
```json
{
  "sub": "admin",
  "roles": ["ADMIN"],
  "iat": 1742389200,
  "exp": 1742392800
}
```

### Step 5: Change Expiration

In `order-security-server/application.yml`:
```yaml
security:
  jwt:
    expiration: 7200    # 2 hours (in seconds)
```

The default is 3600 (1 hour) if not set.

### Step 6: Externalize RSA Key for Production

Replace in-memory generation with a loaded PEM key:

```java
@Configuration
public class KeyConfig {

    @Value("${security.jwt.private-key-path:classpath:keys/private.pem}")
    private Resource privateKeyResource;

    @Value("${security.jwt.public-key-path:classpath:keys/public.pem}")
    private Resource publicKeyResource;

    @Bean
    public RSAKey rsaKey() throws Exception {
        // Load PEM files
        RSAPrivateKey privateKey = loadPrivateKey(privateKeyResource);
        RSAPublicKey publicKey = loadPublicKey(publicKeyResource);
        return new RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID("main-key")
            .build();
    }
}
```

Generate keys:
```bash
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
```

Store in environment variable or Vault, never in git.

---

## Validation Checklist

- [ ] JWT claim name is `"roles"` (not `"scope"`, `"authorities"`, `"role"`)
- [ ] Role values are plain strings: `"ADMIN"`, `"MANAGER"`, `"BUYER"`, `"VIEWER"`
- [ ] `JWSAlgorithm.RS256` used (not HS256)
- [ ] `keyID` in JWT header matches `rsaKey.getKeyID()` → `"main-key"`
- [ ] `order-security-server` does NOT configure `oauth2ResourceServer` in its `SecurityConfig`
- [ ] JWKS endpoint only exposes public key: `rsaKey.toPublicJWK()`
- [ ] `security.jwt.expiration` configured in `application.yml`

## Common Mistakes

- Using `"scope"` or `"authorities"` as the claim name — microservices use `"roles"` claim name,
  they won't find the authorities.
- Using `new RSASSASigner(rsaKey.toPublicJWK())` — you must sign with the PRIVATE key.
  `toPublicJWK()` strips the private key and signing will fail.
- Adding `oauth2ResourceServer` to `order-security-server` — it will try to validate its own
  tokens and reject login requests.
- Exposing the full `RSAKey` (with private key) in the JWKS endpoint instead of `toPublicJWK()`.
