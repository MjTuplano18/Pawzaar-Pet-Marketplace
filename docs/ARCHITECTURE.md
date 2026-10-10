# Pawzaar API — Architecture & Codebase Guide

A beginner-friendly, end-to-end explanation of **what every file in this repository does, why it
exists, and how the pieces talk to each other**.

Read this top to bottom once, then use it as a reference. Every section is written so that a
reader who knows core Java and OOP — but not Spring Boot — can follow along.

---

## Table of contents

1. [What Pawzaar is](#1-what-pawzaar-is)
2. [Tech stack (and why those versions matter)](#2-tech-stack-and-why-those-versions-matter)
3. [How to run everything](#3-how-to-run-everything)
4. [The big picture: startup and request flow](#4-the-big-picture-startup-and-request-flow)
5. [Package layout and why it looks like this](#5-package-layout-and-why-it-looks-like-this)
6. [File-by-file guide](#6-file-by-file-guide)
7. [Journey of one request (end to end)](#7-journey-of-one-request-end-to-end)
8. [The error system](#8-the-error-system)
9. [Security model](#9-security-model)
10. [Database, entities and migrations](#10-database-entities-and-migrations)
11. [Testing strategy](#11-testing-strategy)
12. [Architecture rules we follow](#12-architecture-rules-we-follow)
13. [Debugging playbook](#13-debugging-playbook)
14. [Toolchain traps we already hit](#14-toolchain-traps-we-already-hit)
15. [Annotation & import glossary](#15-annotation--import-glossary)
16. [Study order + where we go next](#16-study-order-where-we-go-next)

---

## 1. What Pawzaar is

A **Philippines-focused pet marketplace API**. Buyers browse pets for sale; sellers post listings.

Currently implemented (through Step 10):

| Capability | Status |
|---|---|
| Liveness endpoint | ✅ done |
| Public, paginated pet browsing (`GET /pets`) | ✅ done |
| **Search & filtering** on `GET /pets` (optional filters + sort allowlist) | ✅ done (Step 10) |
| Pet detail with a clean 404 (`GET /pets/{id}`) | ✅ done |
| One consistent error format (RFC 9457) | ✅ done |
| Automated tests (web slice + DB slice + unit) | ✅ 190 tests |
| User registration with hashed passwords | ✅ done |
| Login + JWT issuance, token verification on protected routes | ✅ done (Step 6.3) |
| Refresh tokens: rotation, revocation, `POST /auth/refresh` + `/auth/logout` | ✅ done |
| Seller endpoints that use the token for **authorization** (ownership) | ✅ done (Step 7) |
| `USER` → `SELLER` promotion on first listing | ✅ done |
| **Role-based authorization** via `@PreAuthorize` (seller-only writes) | ✅ done (Step 9) |
| **Auth rate limiting** (per-IP token bucket → `429`) | ✅ done (Step 11) |
| **Per-account limits** (listings/seller, images/listing → `409`) | ✅ done |
| **Moderation** — new listings go live immediately; review queue deferred | 📝 decision |
| **Listing images**: owner upload, byte-level validation, public serve | ✅ done (Step 12) |
| **User profile** (displayName/phone/bio) + one avatar per user | ✅ done (Step 13) |
| Strict CORS allowlist | ✅ done |
| OpenAPI/Swagger UI, profiles, actuator health | ✅ done |
| GitHub Actions CI, `README`, multi-stage Dockerfile | ✅ done |


---

## 2. Tech stack (and why those versions matter)

| Piece | Version | What it does for us |
|---|---|---|
| Java | **25** target (you may run on 26) | The language. `pom.xml` sets `<java.version>25</java.version>`. |
| Spring Boot | **4.1.1** | The framework: auto-configuration, embedded Tomcat, test slices. |
| Spring Framework | **7** | Core Spring: DI, MVC, transactions. |
| Spring Security | **7.x** (managed by Boot) | The filter chain in front of every request. |
| Hibernate ORM | **7.4.x** | Maps Java objects to tables (`Pet` ⇄ `pets`). |
| Spring Data JPA | managed by Boot | Generates the repository implementation for us. |
| Flyway | managed by Boot | Runs versioned SQL migrations in order, once. |
| Jackson | **3** (Boot 4's default) | Turns Java objects into JSON (and back). |
| PostgreSQL | **17** in Docker | The database. Host port **5433**. |
| Maven | 3.9.16 (via `mvnw`) | Build + dependency management + test runner. |

> ⚠️ **Boot 4 moved packages.** Test annotations live in new packages (`@WebMvcTest` is now
> `org.springframework.boot.webmvc.test.autoconfigure`, `@DataJpaTest` is
> `org.springframework.boot.data.jpa.test.autoconfigure`). Always copy imports from *this*
> project, not from a Boot 3 tutorial.

---

## 3. How to run everything

```bash
# 1) start the database (Docker)
docker compose up -d

# 2) run the app (port 8080)
.\mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run            # macOS / Linux

# 3) run all tests
.\mvnw.cmd test

# 4) optional: run one test class / one method
.\mvnw.cmd test "-Dtest=PetControllerTest"
.\mvnw.cmd test "-Dtest=PetRepositoryTest#findByIdWithRandomUuidIsEmpty"
```

Quick checks:

| URL | Expect |
|---|---|
| `http://localhost:8080/api/v1/health` | `200` `{"status":"ok"}` |
| `http://localhost:8080/api/v1/pets` | `200` + a page of pets |
| `http://localhost:8080/api/v1/pets/<real-id>` | `200` + full pet JSON |
| `http://localhost:8080/api/v1/pets/00000000-0000-0000-0000-000000000000` | `404` + `ProblemDetail` |
| `http://localhost:8080/api/v1/users` | `403` (locked) |

Postman collection: `postman/Pawzaar_API.postman_collection.json` (import it via
**Import → Files**).

---

## 4. The big picture: startup and request flow

### 4.1 Startup — happens once

```
PawzaarApiApplication.main()
   │
   ├─ ① COMPONENT SCAN          Spring walks com.pawzaar.** looking for annotations:
   │                            @SpringBootApplication, @Configuration, @Service,
   │                            @RestController, JpaRepository interfaces
   │
   ├─ ② AUTO-CONFIGURATION      "What's on the classpath?" → wire it up:
   │                            starter-webmvc   → embedded Tomcat + Jackson
   │                            starter-data-jpa → Hibernate + HikariCP connection pool
   │                            starter-security → the security filter chain
   │                            starter-flyway   → run pending migrations (V1 … V7)
   │
   ├─ ③ SCHEMA VALIDATION       ddl-auto: validate → "does Pet match the pets table?"
   │                            (any mismatch → startup FAILS loudly)
   │
   ├─ ④ CODE GENERATION         Spring Data reads JpaRepository<Pet, UUID> and generates
   │                            an implementation class in memory
   │                            (log line: "Found 2 JPA repository interfaces")
   │
   ├─ ⑤ DEPENDENCY INJECTION    Spring constructs your objects and passes their
   │                            collaborators in — see below
   │
   └─ ⑥ TOMCAT STARTS           app now waits for requests on :8080
```

**Dependency injection, demystified.** You never wrote `new AuthService(...)`:

```java
public AuthService(UserRepository repo, PasswordEncoder encoder) { ... }
//                ↑                  ↑                        ↑
// Spring: "I need a UserRepository — I generated one in step ④.
//          I need a PasswordEncoder — someone declared it as a @Bean.
//          Here you go."
```

Everything labelled `@Service`, `@Configuration`, `@RestController`, or extending
`JpaRepository` gets created by Spring and wired automatically.

### 4.2 A request — happens over and over

```
HTTP  GET /api/v1/pets/8f3a...
  │
  ▼
Tomcat (embedded web server)
  │
  ▼ ① Spring Security filter chain  (SecurityConfig)
  │    "Is this URL public?"  → not on the whitelist? 403/401, request dies HERE
  │
  ▼ ② DispatcherServlet (one servlet for the whole app)
  │    "Which @GetMapping matches this path?" → PetController.getPet
  │
  ▼ ③ PetController  (thin: parse, delegate, return DTO)
  │
  ▼ ④ PetService  @Transactional opens a DB transaction HERE
  │    ├─ PetRepository.findById(id)  →  Optional<Pet>
  │    ├─ empty?  → throw PetNotFoundException
  │    └─ entity → PetResponse  (the translator step)
  │    transaction CLOSES → the Pet entity is now detached; only the DTO escapes
  │
  ▼ ⑤ PetRepository  (Spring Data's generated proxy)
  │    SELECT * FROM pets WHERE id = $1
  │
  ▼ ⑥ PostgreSQL  → rows become Pet entities
  │
  ▼ ⑦ Jackson serializes PetResponse → {"id": ..., "title": ...}
  ▼ ⑧ response travels back out through the security filters → to the client
```

Two things to internalize:

* **The arrows only point downward.** Controller → Service → Repository → DB. Nothing points
  back up. That single rule *is* the architecture.
* **Entities stop at the service.** `Pet` is a JPA-managed, mutable object. It never becomes
  JSON — a DTO does.

---

## 5. Package layout and why it looks like this

We use **package-by-feature**: everything about pets lives under `pet/`, everything about users
under `user/`. Inside each feature, the layers get their own sub-package.

```
com.pawzaar
├── PawzaarApiApplication.java    entry point
├── common/                      cross-cutting code (not tied to one feature)
│   ├── HealthController.java     liveness probe
│   └── GlobalExceptionHandler.java   the ONE place exceptions become HTTP responses
├── config/                      framework wiring
│   ├── SecurityConfig.java       who may access what
│   ├── CorsConfig.java           which browser origins may call the API
│   ├── JwtConfig.java            issue + verify tokens, token lifetimes
│   └── PasswordEncoderConfig.java    how passwords are hashed
└── pet/                         ── the "pet" feature ──
    ├── Pet.java                 entity (how a pet is STORED)
    ├── Species.java             enums (the domain vocabulary)
    ├── PetStatus.java
    ├── PetNotFoundException.java    domain error (no HTTP knowledge)
    ├── ForbiddenPetAccessException.java
    ├── InvalidPetStatusException.java
    ├── dto/                     the JSON shapes we expose
    │   ├── PetSummary.java      list shape (lean)
    │   └── PetResponse.java     detail shape (full)
    ├── repository/              data access
    │   └── PetRepository.java
    ├── service/                 business rules + transactions
    │   └── PetService.java
    └── controller/              HTTP layer
        └── PetController.java

└── user/                         ── the "user" feature (Step 6) ──
    ├── User.java, Role.java
    ├── RefreshToken.java        entity: stored (hashed) refresh tokens
    ├── EmailAlreadyRegisteredException.java
    ├── InvalidCredentialsException.java
    ├── InvalidRefreshTokenException.java
    ├── dto/       RegisterRequest, UserResponse, LoginRequest, RefreshRequest, TokenResponse
    ├── repository/ UserRepository.java, RefreshTokenRepository.java
    ├── service/    AuthService.java     (register, login, refresh, logout)
    └── controller/ AuthController.java
```

**Why this and not folders called `controllers/`, `services/`, `entities/`?**
Because you navigate by *feature*: "show me everything about pets" = expand one folder. With
top-level layer folders you'd have to hunt across five directories and guess which files belong
together. Both styles exist in industry; this one scales better and keeps related code close.

**Feature-specific vs cross-cutting** — the rule that decides where a class goes:

| Question | If yes → |
|---|---|
| Does this class only make sense for one feature? | put it in that feature (`pet/`, `user/`) |
| Would it be used by *every* feature? | put it in `common/` or `config/` |

---

## 6. File-by-file guide

### 6.1 `PawzaarApiApplication`

Entry point. `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` +
`@ComponentScan`: "scan my packages for beans and auto-configure the rest from the classpath".

### 6.2 `common/HealthController`

```
GET /api/v1/health  →  {"status":"ok"}
```

The smallest complete example of "expose JSON over HTTP":

| Annotation | Meaning |
|---|---|
| `@RestController` | `@Controller` + `@ResponseBody`: the returned object is serialized to JSON |
| `@RequestMapping("/api/v1")` | class-level URL prefix (API versioning lives here) |
| `@GetMapping("/health")` | this method answers `GET /api/v1/health` |

### 6.3 `common/GlobalExceptionHandler` — the error system

A `@RestControllerAdvice` (a *global* interceptor) with one `@ExceptionHandler` method per
exception type. Controllers and services never write error responses; they just throw.

| Exception | Status | Title |
|---|---|---|
| `PetNotFoundException` | 404 | `Pet not found` |
| `ForbiddenPetAccessException` | 403 | `Access denied` |
| `InvalidCredentialsException` | 401 | `Invalid credentials` |
| `InvalidRefreshTokenException` (bad/expired/used refresh token) | 401 | `Invalid refresh token` |
| `EmailAlreadyRegisteredException` | 409 | `Email already registered` |
| `InvalidPetStatusException` (seller set an admin-only status) | 400 | `Invalid listing status` |
| `MethodArgumentNotValidException` (`@Valid` failure) | 400 | `Validation failed` + `errors[]` |
| `MethodArgumentTypeMismatchException` (bad UUID / param) | 400 | `Invalid request parameter` |
| `HttpMessageNotReadableException` (malformed JSON) | 400 | `Malformed request body` |
| `HttpRequestMethodNotSupportedException` (wrong verb) | 405 | `Method not allowed` |
| `HttpMediaTypeNotSupportedException` (wrong content-type) | 415 | `Unsupported media type` |
| `OptimisticLockingFailureException` (`@Version` race) | 409 | `Concurrent modification` |

The four handlers for `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException`,
`HttpRequestMethodNotSupportedException` and `HttpMediaTypeNotSupportedException` cover errors
Spring raises *before* any controller runs (bad path variable, unparseable body, wrong method,
wrong content-type). Without them those responses fall back to Spring's default `/error` page — a
different JSON shape and `application/json` instead of `application/problem+json`. They are what
makes "one error format" actually true.

### 6.4 `config/SecurityConfig`

The filter chain, built once at startup and applied to **every** request:

```java
http.csrf(csrf -> csrf.disable());                    // no cookies → no CSRF
http.cors(Customizer.withDefaults());                 // browser CORS rules from CorsConfig
http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/health").permitAll()
        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
        .requestMatchers("/error").permitAll()
        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/v1/pets", "/api/v1/pets/**").permitAll()
        .requestMatchers(HttpMethod.POST,
                "/api/v1/auth/register", "/api/v1/auth/login",
                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
        .anyRequest().authenticated());                // deny-by-default
return http.build();
```

**Rule: specific permits first, catch-all last.** Rules are evaluated top-down and the first
match wins. If `.anyRequest().authenticated()` came first, every permit below it would be dead
code.

The class also carries **`@EnableMethodSecurity`**. That is the switch that makes
`@PreAuthorize` on individual controller methods actually enforced (see §9). Without it the
annotation is inert — it compiles, it reads correctly, and it does nothing.

### 6.5 `config/PasswordEncoderConfig`

One `@Bean` producing a `PasswordEncoder` (BCrypt). `@Bean` means "Spring, call this method once
at startup and keep the result." Anything in the app can then ask for a `PasswordEncoder`.

Why the *interface*: inject `PasswordEncoder`, not `BCryptPasswordEncoder` — swapping the
algorithm later is a one-file change.

### 6.6 `pet/Pet` — the entity

A JPA entity: a Java object mapped to a database row.

```java
@Entity @Table(name = "pets") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pet { @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id; ... }
```

| Annotation | What it does |
|---|---|
| `@Entity` | this class is a table |
| `@Table(name = "pets")` | the table is called `pets` (the default would be `pet`, a reserved word) |
| `@Id` | primary key |
| `@GeneratedValue(UUID)` | Hibernate generates the UUID before insert |
| `@Column(name=..., nullable=..., length=...)` | describes the column; `ddl-auto: validate` checks these against reality |
| `@Enumerated(EnumType.STRING)` | store `"DOG"`, not `0` |
| `@PrePersist` | callback: fill `createdAt` automatically before INSERT |

**Lombok rules for entities (important, and easy to break):**

* `@Getter` on the **class** — reading is safe.
* `@Setter` **only on editable fields** — `id`, `createdAt` (and `User.passwordHash`) are frozen.
* `@NoArgsConstructor(PROTECTED)` — Hibernate needs a no-arg constructor; `PROTECTED` stops
  application code doing `new Pet()`.
* **Never** `@Data` / `@ToString` / `@EqualsAndHashCode` — they pull every field (and later lazy
  associations) into `toString`/`equals`, causing surprise SQL and enormous log lines.

### 6.7 `pet/Species`, `pet/PetStatus` — enums

`Species {DOG, CAT, OTHER}` and `PetStatus {ACTIVE, SOLD, HIDDEN, PENDING_REVIEW}`. Enums make
invalid states impossible to write (`setStatus("BANANA")` doesn't compile).

### 6.8 `pet/dto` — the DTOs (`PetSummary`, `PetResponse`, `PetFilter`)

Records (immutable carriers) that define the JSON contract:

```
PetSummary   = id, title, species, breed, price, city, province, status, createdAt
PetResponse  = PetSummary + sellerId, ageMonths, description
PetFilter    = species?, province?, city?, breed?, minPrice?, maxPrice?, minAgeMonths?, maxAgeMonths?
```

* The **list** endpoint returns `PetSummary` — no long description for 50 pets.
* The **detail** endpoint returns `PetResponse` — everything the detail page shows.
* `PetFilter` carries the optional search fields; a `null` field means "do not filter on this one".

Why DTOs at all? (1) bandwidth, (2) entities stay internal so the schema can evolve without
breaking clients, (3) nothing sensitive can leak accidentally.

### 6.9 `pet/repository/PetRepository` + `PetSpecifications`

```java
public interface PetRepository extends JpaRepository<Pet, UUID>,
                                        JpaSpecificationExecutor<Pet> { }
```

The two type arguments tell Spring Data everything: **which table** (from `@Table`) and **which
key** (from `@Id`). We inherit `findAll(Pageable)`, `findById`, `save`, `count`, `deleteById`
without writing any SQL. Methods we *do* write are **derived queries**: Spring Data parses the
method name (`findByEmail` → `SELECT … WHERE email = ?`).

`JpaSpecificationExecutor` adds one more method: `findAll(Specification<Pet>, Pageable)`. A
`Specification` is a lambda that receives the Criteria API pieces (`root`, `query`, `cb`) and
returns a predicate. `pet/repository/PetSpecifications.activeMatching(filter)` composes the
filters: it **always** adds `status = ACTIVE`, then adds one clause per non-null field. That is
why eight optional filters don't explode into hundreds of derived-query methods.

### 6.10 `pet/service/PetService` — the brain

```java
@Service
public class PetService {
    private final PetRepository petRepository;                    // constructor injection
    private final UserRepository userRepository;
    public PetService(PetRepository r, UserRepository u) { this.petRepository = r; this.userRepository = u; }

    @Transactional(readOnly = true)                              // one DB transaction
    public PagedResponse<PetSummary> listPets(PetFilter filter, Pageable p) {
        return PagedResponse.of(
                petRepository.findAll(PetSpecifications.activeMatching(filter), p)
                             .map(PetService::toSummary));
    }

    @Transactional(readOnly = true)
    public PetResponse getPet(UUID id) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new PetNotFoundException(id));
        return toResponse(pet);
    }
}
```

Its three jobs:

1. **Transactions** — `@Transactional(readOnly = true)` opens one DB transaction for the whole
   method. Required because `open-in-view: false` means there is *no* database access outside a
   transaction. `readOnly` is a performance hint (skip dirty-checking) — it is **not** a
   security feature.
2. **Business rules** — paging caps, ownership checks, hashing.
3. **Translation** — entity → DTO, so `Pet` never leaves.

It also **promotes the account to `SELLER`** the first time it creates a listing (a seller is just
a user who has posted). The change runs in the *same* transaction as the insert, so the listing and
the role can never disagree. The JWT still says `USER` until the next token is issued, which is why
the refresh-token flow matters: refreshing picks up the new role.

`Page.map(...)` transforms every element of the page: `Page<Pet>` → `Page<PetSummary>`, one
line, no extra SQL.

### 6.11 `pet/controller/PetController` — thin HTTP layer

```java
@GetMapping                       // GET /api/v1/pets?species=DOG&sort=price&order=asc
public PagedResponse<PetSummary> listPets(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Species species,     // …7 more optional filters…
        @RequestParam(defaultValue = "createdAt") String sort,
        @RequestParam(defaultValue = "desc") String order) {

    PageRequest pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(size, 50),                               // hard cap: never trust the client
            parseSort(sort, order));                          // allowlist: createdAt | price | ageMonths
    return petService.listPets(new PetFilter(species, /* … */), pageable);
}
```

Why the cap? Without it, `?size=1000000` makes the database return a million rows and ships
megabytes of JSON. Unbounded pagination is a classic denial-of-service vector.

Why the sort **allowlist**? Handing a raw client string to `Sort.by(...)` lets a typo reach Spring
Data, which throws `PropertyReferenceException` (a 500), and lets clients sort by any column —
including unindexed ones — which is a cheap denial-of-service. `parseSort` accepts only
`createdAt`, `price`, or `ageMonths` and only `asc`/`desc`; anything else becomes a domain
`InvalidSortException` → **400** `problem+json`.

### 6.12 Domain exceptions

`PetNotFoundException`, `ForbiddenPetAccessException`, `InvalidPetStatusException`,
`InvalidSortException`, `EmailAlreadyRegisteredException`, `InvalidCredentialsException`,
`InvalidRefreshTokenException`:
they describe *what went wrong* in business language and know nothing about HTTP.
`GlobalExceptionHandler` decides how each one becomes a status code. Extending `RuntimeException`
means callers aren't forced to write `try/catch` and transactions roll back automatically.

* `InvalidPetStatusException` encodes an **authorization** rule ("only an admin may set
  `PENDING_REVIEW`"). It is thrown from the service — the layer every caller path must pass
  through — not the controller, so a new endpoint can't accidentally bypass it.
* `InvalidSortException` encodes a **presentation** rule ("only these sort keys/directions are
  allowed"). It is thrown from the controller's sort parser, because sorting is a query-string
  concern; the handler still renders it in the shared `problem+json` shape.

### 6.13 `user/User` + `user/Role`

Same shape as `Pet`, mapped to the `users` table from migration V2. Two things to notice:

* **`passwordHash` has no `@Setter`** — a hash can never be swapped by accident. It is set
  exactly once, by `User.register(...)`.
* **`User.register(...)` is a static factory** — the only way to build a `User`. It requires an
  email, an *already-hashed* password and a display name, so the rules are enforced in one place.
  It is `static` on purpose: the entity stays free of Spring dependencies.

### 6.14 `user/repository/UserRepository`

```java
Optional<User> findByEmail(String email);   // SELECT … WHERE email = ?
boolean existsByEmail(String email);        // “is it taken?” (cheap: no row loaded)
```

### 6.15 `user/dto/RegisterRequest` — validation lives here

```java
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String displayName,
        @Pattern(regexp = "^$|^\\+63\\d{9,10}$") String phone) { }
```

The controller runs these with `@Valid` **before** the service is called, so bad input never
reaches the database. The 72-character cap isn't style: BCrypt silently ignores everything after
72 bytes, so longer passwords would be truncated (a real vulnerability).

Its `toString()` is overridden so a logged request object never prints the raw password.

### 6.16 `user/dto/UserResponse` — security by omission

There is no `passwordHash` field. A field that doesn't exist cannot leak.

### 6.17 `user/service/AuthService`

```java
@Transactional                                   // a WRITE transaction (readOnly defaults false)
public UserResponse register(RegisterRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);   // normalize
    if (userRepository.existsByEmail(email)) throw new EmailAlreadyRegisteredException(email);
    User user = User.register(email,
            passwordEncoder.encode(request.password()),               // HASH here, once
            request.displayName().trim());
    ...
    return toResponse(userRepository.save(user));
}
```

Business rules: email uniqueness, email normalization (`Ana@Gmail.com` = `ana@gmail.com`),
hashing at exactly one place, entity → DTO on the way out.

The class exposes four public methods, one per endpoint: `register(...)`, `login(...)`,
`refresh(...)` and `logout(...)`. `login` and `refresh` share a private `issueTokens(user)` helper
that signs the access JWT **and** persists a fresh refresh token (see 6.22).

### 6.18 `user/controller/AuthController`

```java
@PostMapping("/register")
public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
    UserResponse created = authService.register(request);
    return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
}
```

`@Valid` (check the rules) + `@RequestBody` (parse the JSON) + `201 Created` with a `Location`
header — the industry-standard answer for "I created something".

### 6.19 `config/JwtConfig` — issuing and verifying tokens

Built on Spring Security's own support (`spring-boot-starter-oauth2-resource-server`), so **no
hand-written filters**. Six beans:

| Bean | Job |
|---|---|
| `SecretKey` | the HMAC key, decoded from Base64 in `application.yaml` (≥ 256 bits required for HS256) |
| `JwtEncoder` | signs tokens when someone logs in (used by `AuthService`) |
| `JwtDecoder` | verifies signature/algorithm/expiry on every protected request (used by Spring Security) |
| `JwtAuthenticationConverter` | maps the `role` claim → `ROLE_USER` / `ROLE_SELLER` authorities |
| `Duration accessTokenValidity()` | how long an access token lives (default 30 minutes) |
| `Duration refreshTokenValidity()` | how long a refresh token lives (default 7 days) |

The two `Duration` beans exist so `AuthService` receives its lifetimes through the constructor
instead of reading configuration itself — the same "inject it, don't fetch it" idea applied to
settings.

A JWT is three Base64**url** parts: `header.payload.signature`.

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiI2YTU0ZSIsInJvbGUiOiJVU0VSIn0 . SflKxwRJSM…
{"alg":"HS256"}        {"sub":"6a54e621-…","role":"USER",…}   HMAC-SHA256 signature
```

The payload is **not encrypted** — anyone can read it, so it may only contain non-sensitive
facts. The signature is what makes it trustworthy: only the holder of the secret can produce it.

### 6.20 `AuthService.login` + `InvalidCredentialsException`

```java
User user = userRepository.findByEmail(email).orElse(null);
String hashToCompare = (user != null) ? user.getPasswordHash() : dummyHash;
boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCompare);
if (user == null || !passwordMatches) throw new InvalidCredentialsException(email);
return issueTokens(user);   // access JWT + stored (hashed) refresh token
```

Three deliberate security choices:

1. **Timing-attack protection** — BCrypt is deliberately slow, so an "unknown email" answering
   instantly would be a fingerprint attackers use to enumerate accounts. We always run a hash
   comparison, using a dummy hash generated by the encoder when the user does not exist.
2. **No user enumeration** — wrong password and unknown email produce the *same* exception and
   message. The 401 body never echoes the email back.
3. **Algorithm pinning** — the header is built with `HS256` and the decoder is restricted to
   `HS256`, which blocks "alg: none" and algorithm-confusion attacks.

### 6.21 `PasswordEncoderConfig` — `{bcrypt}` prefixes

New hashes are stored as `{bcrypt}$2a$10$…`, so the algorithm is recorded in the data itself and
can be changed later (e.g. to Argon2) without invalidating existing passwords.
`setDefaultPasswordEncoderForMatches(bcrypt)` keeps **legacy unprefixed hashes** working — without
it, logging in as an older user throws `IllegalArgumentException` and answers 500 instead of 401.

### 6.22 Refresh tokens — rotation and revocation

Access tokens are stateless JWTs: fast to verify, **impossible to revoke** before they expire. That
is fine when they are short-lived (30 minutes), but a client can't be asked to type a password every
30 minutes. The refresh token closes that gap.

| | Access token | Refresh token |
|---|---|---|
| Format | signed JWT | opaque random string (256 bits, Base64url) |
| Verified by | signature only (no DB) | database lookup by SHA-256 hash |
| Lifespan | 30 minutes | 7 days |
| Revocable | ❌ | ✅ (a `revoked` column) |
| Sent where | `Authorization: Bearer …` header | request body of `/auth/refresh` and `/auth/logout` |

**Never store the raw refresh token.** `RefreshToken` (table `refresh_tokens`, migration V4) keeps
only `sha256(rawToken)`. If the database leaks, the attacker has hashes, not usable tokens — the
same reason passwords are hashed, applied to a *token*.

The flow, with **rotation**:

```text
POST /auth/login     → {accessToken, refreshToken}
   … 30 min later the access token is expired …
POST /auth/refresh   {refreshToken}
   → look up by hash; reject if unknown / expired / revoked (401)
   → mark the presented token revoked        ← rotation: it is now burned
   → return a NEW pair {accessToken, refreshToken}
POST /auth/logout    {refreshToken}          → revoke it (204, idempotent)
```

Because the old token is burned on every refresh, a stolen refresh token is only useful until the
real client refreshes next — and any replay of the old one is rejected. Both endpoints are public
(whitelisted): a client whose access token just expired must still reach `/auth/refresh`, and the
refresh token in the body *is* the credential. Tokens are never logged; `TokenResponse.toString()`
masks them.

### 6.23 Rate limiting — protecting the auth endpoints

The auth endpoints are the only ones an anonymous attacker can call in a loop, so they are the ones
worth throttling. Pawzaar uses an in-memory **token bucket** per client IP (`common/ratelimit`):

* `InMemoryRateLimiter` — one bucket per key. Each bucket holds up to `capacity` tokens and refills
  `refillTokens` every `refillPeriod`. A request takes one token; an empty bucket is denied, and we
  can compute exactly how long until the next token appears.
* `RateLimitFilter` — a servlet filter that only inspects `POST` on the configured auth paths. When it
  denies, it **delegates** a `RateLimitExceededException` to Spring MVC's `HandlerExceptionResolver`,
  so the `429` is rendered by the same `GlobalExceptionHandler` as every other error — there is no
  second JSON format. It also sets `Retry-After`.
* `RateLimitConfig` — reads `pawzaar.rate-limit.*`, builds the beans, and registers the filter at
  order `-110` (just ahead of Spring Security's chain at `-100`) so a flood is cut off early.
* `RateLimitProperties` — the tunables, all overridable by environment variables.

Two deliberate choices worth defending in an interview:

* **Per-instance, in memory.** Run three replicas and each counts separately. A shared counter
  (Redis) is the production upgrade; for a single-instance portfolio project, in-memory avoids an
  external dependency.
* **`X-Forwarded-For` is off by default.** The header is client-supplied and spoofable, so trusting
  it blindly lets an attacker mint a new bucket per request. Enable it only behind a proxy that
  overwrites the header.

The bucket map is keyed by an attacker-influenced value (the IP), so it is bounded: once it exceeds
`max-keys`, entries idle longer than `bucket-ttl` are evicted.

```text
POST /auth/login  →  RateLimitFilter.tryConsume(ip)  →  allowed?  → controller
                                                       →  denied?   → 429 + Retry-After
```

### 6.24 Listing images — storage, validation, and serving

Images are the first feature where a request writes something *outside* the database. That two-sided
write (a file on disk **and** a row) is exactly why it gets its own service rather than bloating
`PetService` (code lives in `pet/image` and `pet/service/PetImageService`).

```text
POST /pets/{id}/images (multipart "file")
  → PetController
  → PetImageService.upload
      ImageValidator.validate(file)   size cap, type allowlist, magic-byte sniff → ValidatedImage
      ImageStorage.store(bytes)       random UUID + validated extension → storageKey
      PetImageRepository.save(row)    key + content type + size + sort order
  → 201 + Location: /pets/{id}/images/{imageId}
```

| Piece | Responsibility |
|---|---|
| `ImageValidator` | size cap, declared-type allowlist, and **magic-byte sniffing** — the `Content-Type` header is client-supplied, so the bytes must agree with it |
| `ImageStorage` / `LocalImageStorage` | writes to a configured root under a server-generated key; refuses any key that escapes the root |
| `PetImageRepository` | ordered images for a detail page; one batch query for a page's covers (no N+1); id lookups scoped to the owning pet |
| `PetImageService` | ownership; appends new images; deletes row + file; hides a non-`ACTIVE` listing's images from non-owners |
| `PetController` | upload (201 + `Location`), delete (204), and a public byte-streaming GET |

Decisions worth defending in an interview:

* **Validate the bytes, not the filename.** An attacker can name anything `photo.png`; only the first
  bytes prove the format, and the declared type and sniffed type must match.
* **Never expose the storage key.** Clients get an opaque image `id` and an API URL, so storage can
  move to S3/CDN without a client change and cannot be probed for a filesystem path.
* **Keep the two-sided write consistent.** If the row insert fails after the file is written, the file
  is deleted immediately — otherwise it becomes an orphan the app can never reach.
* **Cover image = sort order 0.** Cards need exactly one image, so the list endpoint fetches
  `sort_order = 0` for the whole page in a single query.

### 6.25 Avatars — the same storage layer, one file per user

A user has exactly ONE avatar (a profile picture), so it needs no child table — two nullable columns
(`avatar_storage_key`, `avatar_content_type`) on the `users` row. The columns hold only what serving
requires: an opaque key and the MIME type verified at upload time.

```text
POST /api/v1/me/avatar (multipart "file")   → AuthService.setAvatar
    ImageValidator.validate(file)           same allowlist + magic-byte sniff as pet images
    profileImageStorage.store(bytes)        bucket pawzaar-user-profile (the @Qualifier bean)
    save new key + type on the user row     …then delete the OLD file (deleteQuietly)
GET  /api/v1/me/avatar                      streams the bytes; 404 (AvatarNotFoundException) if none
DELETE /api/v1/me/avatar                    clears the row + deletes the file; idempotent 204
```

Deviations from the pet-image pattern, and why:

* **Ownership is free.** `@AuthenticationPrincipal Jwt` already is this user — the `sub` claim is the
  user id, so there is no "does the caller own it?" check to write.
* **Write new → save row → delete old.** The new file is written first; if the row update fails the
  fresh file is deleted so it cannot become an orphan. Only then is the previous avatar removed.
* **`deleteQuietly` swallows cleanup failures.** The old key is no longer referenced by any row, so a
  leftover object is invisible and harmless; failing the whole upload because a cleanup delete failed
  would be worse.
* **The URL is derived, never stored** (`avatarUrl` = `/api/v1/me/avatar` when a key exists, else
  `null`), so the key can never leak into a JSON response.

### 6.26 Per-account limits and moderation (H6)

Two guards stop a single account from abusing the marketplace (and its storage bill):

| Limit | Where enforced | Response |
|---|---|---|
| Max listings per seller (`pawzaar.limits.max-listings-per-user`, default 20) | `PetService.createPet`, before insert | `409` `QuotaExceededException` |
| Max images per listing (`pawzaar.limits.max-images-per-listing`, default 10) | `PetImageService.upload`, before validating/storing bytes | `409` `QuotaExceededException` |

Both counters ignore soft-deleted (`HIDDEN`) rows, so deleting a listing frees the slot. The image cap
is checked *first*, so a request over the cap never touches the validator or the storage backend.

**Moderation — a deliberate deferral.** New listings go live immediately (`Pet.status` defaults to
`ACTIVE`); there is no human review queue. `PENDING_REVIEW` exists in the enum for a future admin
workflow and is admin-only on the update path, but nothing sets it on create. This is a product
decision, not an oversight: for a marketplace starting out, a review gate would block legitimate
sellers while the abuse it prevents is already bounded by rate limiting and the per-account caps
above. The upgrade path is to set `PENDING_REVIEW` on create and add an admin endpoint that flips it
to `ACTIVE`/`HIDDEN` — the status machine already supports it.

---

## 7. Journey of one request (end to end)

### 7.1 `GET /api/v1/pets?size=500` — the list

| # | Where | What happens |
|---|---|---|
| 1 | `SecurityConfig` | matches `GET /api/v1/pets` → `permitAll` → continue |
| 2 | `PetController` | `@RequestParam` binds `size=500`; `Math.min(500, 50)` = **50** |
| 3 | `PetService` | `@Transactional` opens; `petRepository.findAll(pageable)` |
| 4 | `PetRepository` | `SELECT … LIMIT 50` **+** `SELECT count(*)` |
| 5 | `PetService` | `.map(toSummary)` converts each `Pet` to `PetSummary` |
| 6 | `PetController` | returns `Page<PetSummary>`; transaction closed |
| 7 | Jackson | serializes the `Page` — `content` plus metadata (`totalElements`, `totalPages`, …) |

### 7.2 `GET /api/v1/pets/{id}` — the detail

Same path, plus: `findById` returns `Optional`. Empty → `orElseThrow` → `PetNotFoundException`
→ `GlobalExceptionHandler` → `404` with `application/problem+json`.

```json
{
  "type": "about:blank",
  "title": "Pet not found",
  "status": 404,
  "detail": "No pet exists with id 00000000-0000-0000-0000-000000000000",
  "petId": "00000000-0000-0000-0000-000000000000"
}
```

---

## 8. The error system

**One rule: services throw, one advice translates.** Controllers never build error responses.

```java
throw new PetNotFoundException(id);          // in the service — business language
        ↓ (nobody catches it; it bubbles up)
@ExceptionHandler(PetNotFoundException.class) // in GlobalExceptionHandler — HTTP language
        ↓
ProblemDetail  →  application/problem+json
```

### Status-code cheat sheet

| Code | Means | Example in Pawzaar |
|---|---|---|
| `200` | success with content | `GET /pets` |
| `201` | created; `Location` header points at it | `POST /auth/register` |
| `400` | malformed/invalid input | bad email, bad UUID, unparseable JSON |
| `401` | *who* are you? credentials missing or invalid | protected route without a token; wrong password |
| `403` | we know who you are, but you may not | `POST /pets` while anonymous |
| `404` | the thing doesn't exist | unknown pet id |
| `405` | wrong HTTP method for that URL | `PATCH /api/v1/pets` |
| `409` | valid request, clashes with existing state | email already registered; concurrent edit caught by `@Version` |
| `415` | body has an unsupported content-type | `text/plain` instead of `application/json` |
| `500` | our bug | NPE, failed SQL |

> **403 vs 401:** since Step 6.3 a real authentication mechanism exists (a JWT resource server),
> so an anonymous request to a protected route gets a proper **401** plus a
> `WWW-Authenticate: Bearer` header. Before that — with nothing to check credentials against —
> Spring Security's default entry point answered **403**. Both mean "you may not come in"; 401
> specifically means "authenticate and try again".

> **Why `/error` is whitelisted:** when Spring MVC raises an error (400, 404-no-handler, 405) it
> calls `sendError()`, the container re-dispatches to `/error`, and the security chain runs
> again. If `/error` were protected, every real error would be replaced by a useless `403` with an
> empty body. This cost us an hour of debugging once — keep that line.

---

## 9. Security model

**Deny by default.** Every request passes the filter chain before reaching any controller.

| Rule | Effect |
|---|---|
| `permitAll("/api/v1/health")` | liveness probe is public |
| `permitAll("/actuator/health", "/actuator/health/**")` | host health checks work (only `health` is exposed) |
| `permitAll("/error")` | real error statuses stay visible |
| `permitAll("/swagger-ui/**", "/v3/api-docs/**")` | interactive API docs |
| `permitAll(GET, "/api/v1/pets", "/api/v1/pets/**")` | anyone can **browse** |
| `permitAll(POST, "/api/v1/auth/register", "/api/v1/auth/login")` | you must be able to register/log in without a token |
| `permitAll(POST, "/api/v1/auth/refresh", "/api/v1/auth/logout")` | refresh/logout are authenticated by the refresh token in the body, not by an access token |
| `anyRequest().authenticated()` | everything else needs credentials |

The browser-facing CORS rules live in `CorsConfig` (a `CorsConfigurationSource` bean) and are
switched on by `http.cors(Customizer.withDefaults())`. `allowed-origins` is an explicit allowlist
from configuration — never `*` — and `allowCredentials` is off because we use a bearer header, not
cookies. CORS is a *browser* policy: Postman ignores it, so "it worked in Postman" says nothing
about whether the front end can call the API.

Note the **`HttpMethod.GET`**: anonymous users may *look* at pets, but `POST /api/v1/pets`
stays locked. Browsing is public; acting is not.

Session handling is `STATELESS` — no cookies, no server-side session. That is why CSRF is
disabled (CSRF protection exists to protect cookie-based sessions) and why Step 6 uses an
`Authorization: Bearer <token>` header.

### Tokens, end to end

```
POST /api/v1/auth/login  {"email": "...", "password": "..."}
   → 200 {"accessToken":"eyJhbGci…","refreshToken":"kQ8…","tokenType":"Bearer","expiresInSeconds":1800}

Every later request:
   GET /api/v1/me/pets
   Authorization: Bearer eyJhbGci…

When the access token expires:
   POST /api/v1/auth/refresh  {"refreshToken":"kQ8…"}
   → 200 {new accessToken, new refreshToken}     (the old refresh token is now revoked)
```

The filter chain's `BearerTokenAuthenticationFilter` (installed by
`.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`) does the rest on every protected request:

1. look for the `Authorization` header → absent ⇒ **401** + `WWW-Authenticate: Bearer`
2. verify the signature with our `JwtDecoder` (HS256 only) — **no database lookup**
3. check `exp` — an expired token is rejected exactly like a forged one
4. turn claims into authorities via `JwtAuthenticationConverter`, and put them into the
   `SecurityContext` so controllers can ask "who is this?"

Everything else (`@WebMvcTest` slices, `spring-security-test`, Postman) understands the same
convention, so nothing else had to be written.

### Method-level authorization (`@PreAuthorize`)

The filter chain decides **whether you may reach a URL at all**. `@PreAuthorize` decides
**whether you may perform this specific operation**. They are two gates in a row:

```
request ──▶ filter chain (URL rules) ──▶ controller
                  │                          │
            anonymous? 401            @PreAuthorize fails? 403
```

`@EnableMethodSecurity` (on `SecurityConfig`) turns these annotations on. Each controller method
then declares its own rule, right next to the endpoint it protects:

| Endpoint | Rule | Why |
|---|---|---|
| `POST /pets` | `hasAnyRole('USER', 'SELLER')` | a brand-new account is `USER`; posting is exactly what promotes it to `SELLER`. Requiring `SELLER` here would be a chicken-and-egg trap. |
| `PUT /pets/{id}` | `hasRole('SELLER')` | only sellers edit listings. |
| `DELETE /pets/{id}` | `hasRole('SELLER')` | only sellers delete listings. |
| `GET /me/pets` | `isAuthenticated()` | any logged-in user may read their own listings. |

Three things that trip people up:

* **`hasRole('SELLER')` is not `hasAuthority('SELLER')`.** `hasRole` auto-prepends `ROLE_`, so it
  matches the `ROLE_SELLER` authority that `JwtAuthenticationConverter` builds. `hasAuthority`
  would silently never match.
* **Roles ride in the token.** Promote a `USER` to `SELLER` in the database and their *current*
  access token still says `USER` until it expires or is refreshed. In that window they can
  `create` but get `403` on `update`/`delete`. This is the normal cost of stateless JWTs; a fresh
  `/auth/refresh` re-reads the role and mints a token that works. (Live-verified in Step 9.)
* **Two different `403`s.** A denial from the *method* check throws `AccessDeniedException`,
  which happens inside MVC, so `GlobalExceptionHandler` catches it and returns the normal
  `ProblemDetail` body. A denial from the *URL* rules happens before MVC, so the security filter
  chain answers it (and a bare filter-chain 403 has an empty body). Method security is the kind
  you will see most often.

**Authorization still lives in `PetService` too.** `@PreAuthorize` answers "is this a seller?";
ownership ("is this *your* pet?") can only be answered from the database, so the service keeps
its check. The controller keeps requests cheap; the service keeps them correct.

---

## 10. Database, entities and migrations

### Tables (from `V2__create_users_and_pets.sql`)

```
users:  id, email(UNIQUE), password_hash, display_name, phone?, role, verified, created_at
pets:   id, seller_id → users(id), title, species, breed?, age_months, price,
        description?, city, province, status, created_at
pet_images: id, pet_id → pets(id), storage_key(UNIQUE), content_type, size_bytes, sort_order, created_at
refresh_tokens: id, user_id → users(id), token_hash(UNIQUE), expires_at, revoked, created_at
```

Indexes on `pets`: `(status, created_at DESC)`, `(species, breed)`, `(province, city)`,
`(price)`, `(seller_id)`, a trigram index on `title`, and — added in Step 10 — `(age_months)`,
`(city)`, and a GIN trigram index on `lower(breed)` for the case-insensitive breed search.
`V2` already covered the other filter columns, so `V6` adds only what was genuinely missing.

`pet_images` has one index, `(pet_id, sort_order)`, which serves both "a pet's images in order" and
the batch cover lookup (`pet_id`, `0`) used by the list endpoint.

### Migrations

| File | What it did |
|---|---|
| `V1__init.sql` | enabled the `pg_trgm` extension (fuzzy text search) |
| `V2__create_users_and_pets.sql` | created both tables + indexes |
| `V3__seed_sample_pets.sql` | 1 demo seller + 3 demo pets |
| `V4__add_sex_updated_at_refresh_tokens.sql` | added `sex`, `updated_at`, `version` (optimistic locking) to `pets`; created `refresh_tokens` (now used by the refresh-token flow) |
| `V5__fix_seed_user_password.sql` | gave the V3 seed seller a real BCrypt hash (V3 had stored a placeholder, so it could never log in) |
| `V6__add_pet_search_indexes.sql` | added the search indexes the query patterns needed but `V2` lacked: `(age_months)`, `(city)`, and `gin(lower(breed) trgm)` |
| `V7__create_pet_images.sql` | created `pet_images` (FK to `pets`, `ON DELETE CASCADE`) + an index on `(pet_id, sort_order)` |

**Rules:**

* ✅ Add new migrations as `V4__….sql`, `V5__….sql`, … (append-only).
* ❌ **Never edit a migration that already ran.** Flyway stores a checksum of each file; changing
  it makes startup fail with `Migration checksum mismatch`. This bit us once: `V3` was edited
  during development; the file was restored and the correction moved into `V5`. If you ever do
  edit one accidentally, `Flyway.repair()` realigns the stored checksum — but the *data* still
  reflects the original migration, so an append-only fix is still required.
* Flyway runs pending migrations automatically at startup (you'll see `Migrating schema "public"
  to version "7 - create pet images"`).

### Two JPA settings worth knowing (`application.yaml`)

```yaml
jpa:
  open-in-view: false        # entities are usable ONLY inside a transaction → services must
                             # use @Transactional; stops "lazy load outside session" bugs
  hibernate:
    ddl-auto: validate       # Hibernate NEVER creates/alters tables (Flyway's job); it only
                             # CHECKS that entities match the schema and fails startup otherwise
```

### Entities vs DTOs — the boundary rule

```
Repository  →  returns Pet (ENTITY)
Service     →  converts Pet → PetResponse   ← the only place this happens
Controller  →  returns PetResponse (DTO)     ← entities never reach here
```

---

## 11. Testing strategy

| Test | Boots | Database? | What it proves |
|---|---|---|---|
| `PetControllerTest` (`@WebMvcTest`) | web layer only | ❌ (service is mocked) | status codes, JSON shape, 404/bad-UUID/malformed-JSON all in `ProblemDetail` format, security whitelist, `@PreAuthorize` role rules (incl. the `403` body), query params → `PetFilter`, sort allowlist (`400` on bad input), paging caps, image upload/serve/delete (`201`/`204`/`415`/`404`) |
| `PetRepositoryTest` (`@DataJpaTest`) | JPA layer | ✅ (real Docker Postgres, rolled back after each test) | queries, pagination counts, `Optional` behaviour, status filtering, **and the Specifications** (species/province/city/breed/price/age filters, AND-combination, never returning non-`ACTIVE`, sort) |
| `PetServiceTest` (plain Mockito) | nothing (no Spring) | ❌ repository is a mock | business rules: `ACTIVE`-only listing, ownership, admin-only status rejected, `USER`→`SELLER` promotion |
| `AuthServiceTest` (plain Mockito, real BCrypt) | nothing | ❌ repositories are mocks | login rules (correct/wrong/unknown password, legacy hashes, normalization) **and** refresh rotation/revocation + idempotent logout |
| `AuthControllerTest` (`@WebMvcTest`) | web layer | ❌ (service mocked) | login/refresh/logout are public, token JSON shape, 401 problem details, 400 validation |
| `InMemoryRateLimiterTest` (plain JUnit, fake clock) | nothing | ❌ | token-bucket behaviour: burst, deny, refill over time, per-key isolation, stale-bucket eviction, config validation |
| `RateLimitFilterTest` (plain JUnit, mock servlet) | nothing | ❌ | only `POST` on configured paths is limited; a denial routes through the resolver and never reaches the chain |
| `GlobalExceptionHandlerTest` (plain JUnit) | nothing | ❌ | `RateLimitExceededException` → `429` + `Retry-After` + `problem+json` title |
| `ImageValidatorTest` (plain JUnit) | nothing | ❌ | size cap, type allowlist, and magic-byte sniffing (a renamed/lying `Content-Type` is rejected) |
| `LocalImageStorageTest` (plain JUnit, `@TempDir`) | nothing | ❌ | round-trip, unique keys, delete, path-traversal rejection |
| `PetImageRepositoryTest` (`@DataJpaTest`) | JPA layer | ✅ | ordered images, batch cover lookup, pet-scoped id lookup, `ON DELETE CASCADE` |
| `PetImageServiceTest` (plain Mockito) | nothing | ❌ | owner-only upload/delete, file cleanup when the row write fails, hiding a non-`ACTIVE` listing's images |
| `PawzaarApiApplicationTests` (`@SpringBootTest`) | everything | ✅ | the whole context starts |

Current total: **118 tests**, all green with `mvn test`.

```bash
.\mvnw.cmd test                                            # all tests
.\mvnw.cmd test "-Dtest=PetControllerTest"                 # one class
.\mvnw.cmd test "-Dtest=PetRepositoryTest#findByIdWithRandomUuidIsEmpty"   # one method
```

For the whole CI-equivalent check locally, run **`.\verify-local.ps1`**. It does four things:
ensures the DB is up, runs `mvnw verify`, builds the Docker image, then starts the app and drives
the real HTTP API (register → create → stale-role 403 → refresh → update) before deleting its own
probe data. Use `-SkipDockerImage`, `-SkipLive`, or `-SkipTests` to run just part of it.

Two annotations you'll meet:

* `@MockitoBean` — replaces a real bean in a test with a fake double (no DB needed).
* `@AutoConfigureTestDatabase(replace = NONE)` — tells `@DataJpaTest` to use the **real**
  database instead of swapping in an in-memory one (we have no H2 on the classpath).

Tests run against the real schema, but each `@DataJpaTest` runs in a transaction that is rolled
back, so your data is never polluted.

---

## 12. Architecture rules we follow

1. **Layering:** Controller → Service → Repository → PostgreSQL. Never skip a layer; never go up.
2. **Entities never leave the service.** Controllers return DTOs (records) only.
3. **Constructor injection only.** No field `@Autowired`.
4. **One error format.** Throw domain exceptions; one `@RestControllerAdvice` renders them.
5. **Pagination on every list endpoint, hard-capped at 50.**
6. **Versioned URLs:** everything under `/api/v1`.
7. **Transactions in the service**, always explicit (`readOnly = true` for reads).
8. **Schema changes only via new Flyway migrations.**
9. **Package-by-feature**, with layer sub-packages.

---

## 13. Debugging playbook

When something breaks, ask: **which stage of the flow lied?**

```
Symptom                          Most likely stage               Where to look
--------------------------------------------------------------------------------------
403, empty body                  filter chain (stage 1)         SecurityConfig URL rules?
403 + JSON body                  @PreAuthorize or ownership      role rule, or PetService owner check
401                              auth present but token missing  (Step 6.4)
404 + JSON body                  your exception (stage 4)        GlobalExceptionHandler
405 / 400                        your code (stages 2–3)          controller, DTO validation
500                              a bug in service/repository     stack trace in the console
startup failure                  config / schema / migrations   ddl-auto validate, Flyway,
                                                                  application.yaml
slow response                    payload size or N+1 queries     SELECT count(*) logs, DTO size
```

Tricks that paid off here:

* `GET /api/v1/health` returning 403 → security config isn't loaded (or the app wasn't
  **restarted** — Java never hot-reloads).
* `POST` to a whitelisted-all-methods URL returning 403 instead of 405 → something blocks POST
  before MVC; check `/error` and CSRF.
* Turn on `logging.level.org.springframework.security=TRACE` and
  `logging.level.org.springframework.web=DEBUG` to see the real exception behind a 403.
* Verify the running code, not the source: check the log line
  `Found N JPA repository interfaces` and the compiled `.class` timestamps.

---

## 14. Toolchain traps we already hit

Worth remembering, because each cost real time:

| Trap | What happened | Lesson |
|---|---|---|
| **Lombok silently dead on JDK 23+** | `cannot find symbol: getTitle()` although `@Getter` was present | JDK 23 stopped running annotation processors implicitly. Fixed with `<maven.compiler.proc>full</maven.compiler.proc>` in `pom.xml`. |
| **Stale `target/classes/application.yaml`** | config "worked" but only existed in build output | `target/` is disposable; never commit or trust it — `mvn clean` deletes it. |
| **PowerShell mangles JSON quotes** | `curl.exe -d '{...}'` sent invalid JSON; server returned a mystery `403` | Use Postman, `Invoke-RestMethod`, or `--data-binary "@file.json"`. |
| **`403` masking `400`** | real error invisible because `/error` was protected | keep `permitAll("/error")`. |
| **"Works in Postman, fails in the browser"** | the browser blocked the response because no `Access-Control-Allow-Origin` header came back | CORS is a *browser* rule; Postman never enforces it. Configure a `CorsConfigurationSource` and enable `http.cors(...)`. |
| **403, not 401, before auth exists** | no authentication mechanism to challenge against | expected until Step 6.4 |
| **Delegating encoder needs a prefix** | `IllegalArgumentException: each password must have a password encoding prefix` → login answered **500** instead of 401 | store `"{bcrypt}$2a$10$..."`, and set a fallback encoder (`setDefaultPasswordEncoderForMatches`) for hashes written before the change |
| **Hand-written "dummy" BCrypt hash** | a copied hash string made `matches()` throw instead of returning `false` | generate the timing-protection hash with the injected encoder at startup |
| **Spring Security 7 API changes** | `DelegatingPasswordEncoder` has no 3-arg constructor with a fallback encoder any more | check the real class with `javap` before trusting an older example |
| **IntelliJ green, Maven red** | the IDE does its own Lombok processing | trust `mvn test`, not only the IDE. |
| **Repeated `maven.compiler.proc` flag eaten** | `-D...` without quotes in PowerShell | quote `-D` arguments: `"-Dmaven.compiler.proc=full"`. |
| **Editing an applied Flyway migration** | the working-tree `V3` no longer matched the committed one, yet the DB still held the old seed row | migrations are append-only: fix data with a new `V…` file. `Flyway.repair()` only realigns the stored checksum — it does **not** change data. |
| **`@PreAuthorize` denial answered with an empty body** | method security threw `AccessDeniedException`, which escaped MVC and was answered by the security filter chain with no JSON | add an `AccessDeniedException` handler to `GlobalExceptionHandler` so the `403` keeps the RFC 9457 shape. Filter-chain denials (URL rules) still have empty bodies — they happen before MVC. |
| **`hasAuthority('SELLER')` never matches** | the authority is `ROLE_SELLER`, not `SELLER` | use `hasRole('SELLER')`; `hasRole` auto-prepends `ROLE_`. |
| **Forgetting `@EnableMethodSecurity`** | `@PreAuthorize` compiled and looked right but enforced nothing | method security is off by default; switch it on on the `SecurityConfig` class. |
| **MockMvc `jwt()` bypasses our converter** | a test set the `role` claim yet got `403`; the authority was never created | the post-processor builds the `Authentication` itself and does not run `JwtAuthenticationConverter`; set `.authorities(List.of(new SimpleGrantedAuthority("ROLE_"+role)))` explicitly in tests. |

---

## 15. Annotation & import glossary

### `jakarta.persistence` (mapping objects to tables)

| Import | Meaning |
|---|---|
| `@Entity` | this class is a table |
| `@Table(name=…)` | table name |
| `@Id` | primary key |
| `@GeneratedValue(strategy=UUID)` | generate the key in Java |
| `@Column(name, nullable, length, unique, precision, scale, updatable, columnDefinition)` | column shape |
| `@Enumerated(EnumType.STRING)` | store enum names |
| `@PrePersist` | run before INSERT |

### `lombok`

| Import | Meaning |
|---|---|
| `@Getter` / `@Setter` | generate `getX()` / `setX()` |
| `@NoArgsConstructor(access = PROTECTED)` | the constructor Hibernate needs, not callable from outside |

### Spring core / web

| Import | Meaning |
|---|---|
| `@Service` / `@Configuration` / `@RestController` / `@RestControllerAdvice` | mark a class for Spring with a role |
| `@Bean` | "the object from this method is managed by Spring" |
| `@Transactional(readOnly = true)` | one DB transaction around the method |
| `@GetMapping` / `@PostMapping` / `@RequestMapping` | URL → method |
| `@PathVariable` / `@RequestParam` / `@RequestBody` / `@Valid` | bind path, query, JSON body, and validate it |
| `ResponseEntity` | control status + headers + body |
| `Page<T>` / `Pageable` / `PageRequest` / `Sort` | pagination |
| `ProblemDetail` / `HttpStatus` | RFC 9457 errors + status codes |
| `Optional<T>` | "a value that may be absent" |

### Spring Data JPA

| Import | Meaning |
|---|---|
| `JpaRepository<Entity, IdType>` | base repository (find/save/delete/count) |
| `Page`, `PageImpl` | query results + pagination metadata |

### Spring Security

| Import | Meaning |
|---|---|
| `@EnableWebSecurity`, `SecurityFilterChain`, `HttpSecurity` | build the filter chain |
| `SessionCreationPolicy.STATELESS` | no server-side session |
| `PasswordEncoder`, `BCryptPasswordEncoder` | password hashing |

### Testing

| Import | Boot 4 location (verified in this project) |
|---|---|
| `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.jdbc.test.autoconfigure` |
| `@MockitoBean` | `org.springframework.test.context.bean.override.mockito` |
| `MockMvc`, `MockMvcRequestBuilders`, `MockMvcResultMatchers` | `org.springframework.test.web.servlet.*` |

---

## 16. Study order + where we go next

### Recommended reading order (highest understanding per minute)

1. `common/HealthController.java` — the whole HTTP story in 13 lines
2. `pet/controller/PetController.java` + `common/GlobalExceptionHandler.java` — the client-facing vocabulary
3. `pet/service/PetService.java` — transactions, paging, entity→DTO
4. `pet/Pet.java` + `pet/repository/PetRepository.java` — persistence concepts
5. `config/SecurityConfig.java`, `application.yaml`, migrations — supporting cast
6. The `user/*` files last — they're the same patterns, applied to auth

For any file, answer three questions: **what does it promise? what does it need? what does it
protect?** If you can answer those, you understand the file — even if you can't recite its
imports.

### Roadmap

| Step | Content | Status |
|---|---|---|
| 5 | Read-only pet endpoints, error format, tests, Postman, git | ✅ |
| 6.1 | `User` entity, `Role`, `UserRepository` | ✅ |
| 6.2 | Registration, BCrypt, validation, 409 | ✅ |
| 6.3 | **Token decision (JWT) + `POST /api/v1/auth/login`** | ✅ |
| 6.4 | Token verification wired in (403 → 401 confirmed) | ✅ |
| 6.5 | Review pass: append-only `V5` seed fix, admin-only status guard, full `ProblemDetail` coverage, actuator health | ✅ |
| 7 | Seller actions: `POST/PUT/DELETE /pets` with ownership authorization, `GET /me/pets` | ✅ (PUT, not PATCH) |
| 8.1 | Portfolio polish: OpenAPI ✅, `run-dev.ps1` ✅ | ✅ |
| 8.2 | Refresh tokens: rotation, revocation, `POST /auth/refresh` + `/auth/logout` | ✅ |
| 8.3 | Strict CORS allowlist; `USER` → `SELLER` promotion on first listing | ✅ |
| 8.4 | GitHub Actions CI, `README`, multi-stage Dockerfile | ✅ |
| 9 | Role-based authorization: `@EnableMethodSecurity` + `@PreAuthorize` on writes; `403` kept in `ProblemDetail` | ✅ |
| 10 | Search & filtering: optional filters (`Specification`), sort allowlist, `V6` indexes, tests/docs/Postman | ✅ |
| 11 | Rate limiting: token bucket, auth filter, 429 `problem+json`, tests/docs/Postman | ✅ |
| 12 | Listing images: storage abstraction, byte-level validation, upload/delete/serve, cover in lists | ✅ |
| 13 | Next up: admin moderation & reporting | ⏳ |

---

*Maintained by the Pawzaar project. If something in this document is wrong or out of date,
that's a bug — fix the code and this file together.*
