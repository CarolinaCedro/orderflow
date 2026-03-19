# Skill: Apply RBAC with @PreAuthorize

## Purpose

Correctly apply role-based access control to REST endpoints using `@PreAuthorize` with the
exact authority expression format used in OrderFlow.

## When to Use

- Adding `@PreAuthorize` to controller methods extending `AbstractController`.
- Creating new controllers with custom endpoints.
- Reviewing or debugging authorization failures (403 responses).
- Adding a new role to the system.

## Prerequisites

- `MicroserviceSecurityConfig` autoconfigured (via `order-utils`) with `@EnableMethodSecurity`.
- JWT contains `"roles"` claim with plain role names (no `ROLE_` prefix).
- `JwtGrantedAuthoritiesConverter` configured with `setAuthorityPrefix("")`.

## Knowledge References

- `.agents/rules/security-rules.md` — RBAC matrix, claim names, authority prefix
- `.agents/knowledge/spring/security.md` — @PreAuthorize expressions, JwtAuthenticationConverter
- `.agents/knowledge/orderflow/seguranca.md` — default users and roles

---

## Steps

### Step 1: Verify @EnableMethodSecurity is Active

`MicroserviceSecurityConfig` in `order-utils` has `@EnableMethodSecurity`. Since it's
autoconfigured, all microservices using `order-utils` have method security enabled.

If a custom `SecurityFilterChain` is declared, ensure it does NOT remove `@EnableMethodSecurity`:

```java
@AutoConfiguration
@EnableWebSecurity
@EnableMethodSecurity    // This activates @PreAuthorize
public class MicroserviceSecurityConfig { ... }
```

### Step 2: Understand Authority Format

JWT `"roles"` claim: `["ADMIN"]`
After `JwtGrantedAuthoritiesConverter` with `setAuthorityPrefix("")`:
→ `GrantedAuthority` = `"ADMIN"` (NO prefix)

Therefore: ALWAYS use `hasAuthority(...)`, NEVER `hasRole(...)`:
```java
// CORRECT
@PreAuthorize("hasAuthority('ADMIN')")
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")

// WRONG — hasRole adds ROLE_ prefix → looks for "ROLE_ADMIN" in authorities → 403
@PreAuthorize("hasRole('ADMIN')")
```

### Step 3: Apply @PreAuthorize to ALL Methods in AbstractController Extensions

Every method in a controller extending `AbstractController` MUST be overridden with `@PreAuthorize`:

```java
@RestController
@RequestMapping("/orderflow/v1/order")
public class OrderController extends AbstractController<Order> {

    private final OrderServiceImpl orderService;

    public OrderController(OrderServiceImpl orderService) {
        this.orderService = orderService;
    }

    @Override
    protected AbstractService<Order> getService() {
        return orderService;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER')")   // BUYER can create orders
    public ResponseEntity<Order> save(Order value, String returnEntity) {
        return super.save(value, returnEntity);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<Order> update(String id, Order model) {
        return super.update(id, model);
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")                           // Only ADMIN can delete
    public void deleteById(String id) {
        super.deleteById(id);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<Order> findById(String id) {
        return super.findById(id);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<List<Order>> list(Map<String, String> allRequestParams) {
        return super.list(allRequestParams);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<Long> count(Map<String, String> allRequestParams) {
        return super.count(allRequestParams);
    }
}
```

### Step 4: Custom Endpoint Authorization

For endpoints not in `AbstractController`:

```java
// Admin-only custom operation
@PostMapping("/{id}/approve")
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
public ResponseEntity<Order> approveOrder(@PathVariable String id) {
    return ResponseEntity.ok(orderService.approveOrder(id));
}

// Any authenticated user (just needs a valid JWT)
@GetMapping("/my-orders")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<List<Order>> getMyOrders(Authentication auth) {
    return ResponseEntity.ok(orderService.findByCustomerId(auth.getName()));
}

// Dynamic check: only the order owner or an ADMIN can access
@GetMapping("/{id}/details")
@PreAuthorize("hasAuthority('ADMIN') or @orderService.isOwner(#id, authentication.name)")
public ResponseEntity<Order> getOrderDetails(@PathVariable String id) {
    return super.findById(id);
}
```

### Step 5: Service-Level @PreAuthorize

For fine-grained control at the service layer (beyond controller):

```java
@Service
public class OrderServiceImpl extends AbstractService<Order> implements OrderService {

    @PreAuthorize("hasAuthority('ADMIN')")
    public void forceCompleteOrder(String id) {
        // Admin override to force complete an order
    }
}
```

Service-level `@PreAuthorize` works because `@EnableMethodSecurity` is class-agnostic.

### Step 6: Getting Current User in Service Layer

```java
// Via SecurityContextHolder (in any @Component)
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String username = auth.getName();

// Via SecurityContextHelper (from order-utils)
String username = SecurityContextHelper.getCurrentUsername();

// Via Authentication injected by Spring MVC
@GetMapping("/my-orders")
public ResponseEntity<List<Order>> getMyOrders(Authentication authentication) {
    String currentUser = authentication.getName();
    return ResponseEntity.ok(orderService.findByCustomerId(currentUser));
}
```

### Step 7: Testing Authorization

Test each role:
```bash
# Get admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/order-security-server/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.accessToken')

# Get buyer token
BUYER_TOKEN=$(curl -s -X POST http://localhost:8080/order-security-server/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"buyer1","password":"buyer123"}' | jq -r '.accessToken')

# BUYER can create order — expect 200
curl -X POST http://localhost:8080/order-service/orderflow/v1/order \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"c1","customerName":"Test","items":[...],"totalAmount":100}'

# BUYER cannot delete — expect 403
curl -X DELETE http://localhost:8080/order-service/orderflow/v1/order/some-id \
  -H "Authorization: Bearer $BUYER_TOKEN"
```

---

## Validation Checklist

- [ ] All 6 `AbstractController` methods overridden with `@PreAuthorize`
- [ ] `hasAuthority(...)` used (never `hasRole(...)`)
- [ ] Role names match exactly: `'ADMIN'`, `'MANAGER'`, `'BUYER'`, `'VIEWER'`
- [ ] Delete restricted to `ADMIN` only
- [ ] Read operations (findById, list, count) allow all 4 roles
- [ ] `@EnableMethodSecurity` active (via `MicroserviceSecurityConfig`)

## Common Mistakes

- Using `hasRole('ADMIN')` — Spring prepends `ROLE_` making it `ROLE_ADMIN`, which doesn't
  match `ADMIN` in the authorities set. Results in 403 for all authenticated users.
- Not overriding all 6 methods — unoverrideen methods have NO authorization check.
- Putting `@PreAuthorize` on private methods — Spring AOP proxy can't intercept them.
- Using `@Secured` instead of `@PreAuthorize` — `@Secured` does not support SpEL expressions.
