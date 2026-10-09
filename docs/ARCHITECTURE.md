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

Currently implemented (Steps 1–6.2):

| Capability | Status |
|---|---|
| Liveness endpoint | ✅ done |
| Public, paginated pet browsing (`GET /pets`) | ✅ done |
| Pet detail with a clean 404 (`GET /pets/{id}`) | ✅ done |
| One consistent error format (RFC 9457) | ✅ done |
| Automated tests (web slice + DB slice) | ✅ 8 tests |
| User registration with hashed passwords | ✅ done |
| Login + JWT issuance, token verification on protected routes | ✅ done (Step 6.3) |
| Seller-only endpoints that use the token for **authorization** | ⏳ Step 7 |
| Seller actions (create/edit/hide a pet) | ⏳ Step 7 |

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
   │                            starter-flyway   → run V1, V2, V3 migrations
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
│   └── PasswordEncoderConfig.java    how passwords are hashed
└── pet/                         ── the "pet" feature ──
    ├── Pet.java                 entity (how a pet is STORED)
    ├── Species.java             enums (the domain vocabulary)
    ├── PetStatus.java
    ├── PetNotFoundException.java    domain error (no HTTP knowledge)
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
    ├── User.java, Role.java, EmailAlreadyRegisteredException.java
    ├── dto/       RegisterRequest.java, UserResponse.java
    ├── repository/ UserRepository.java
    ├── service/    AuthService.java
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
| `EmailAlreadyRegisteredException` | 409 | `Email already registered` |
| `MethodArgumentNotValidException` (`@Valid` failure) | 400 | `Validation failed` + `errors[]` |

### 6.4 `config/SecurityConfig`

The filter chain, built once at startup and applied to **every** request:

```java
http.csrf(csrf -> csrf.disable());                    // no cookies → no CSRF
http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/health").permitAll()
        .requestMatchers("/error").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/v1/pets", "/api/v1/pets/**").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
        .anyRequest().authenticated());                // deny-by-default
return http.build();
```

**Rule: specific permits first, catch-all last.** Rules are evaluated top-down and the first
match wins. If `.anyRequest().authenticated()` came first, every permit below it would be dead
code.

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

### 6.8 `pet/dto/PetSummary` and `pet/dto/PetResponse` — the DTOs

Records (immutable carriers) that define the JSON contract:

```
PetSummary   = id, title, species, breed, price, city, province, status, createdAt
PetResponse  = PetSummary + sellerId, ageMonths, description
```

* The **list** endpoint returns `PetSummary` — no long description for 50 pets.
* The **detail** endpoint returns `PetResponse` — everything the detail page shows.

Why DTOs at all? (1) bandwidth, (2) entities stay internal so the schema can evolve without
breaking clients, (3) nothing sensitive can leak accidentally.

### 6.9 `pet/repository/PetRepository`

```java
public interface PetRepository extends JpaRepository<Pet, UUID> { }
```

The two type arguments tell Spring Data everything: **which table** (from `@Table`) and **which
key** (from `@Id`). We inherit `findAll(Pageable)`, `findById`, `save`, `count`, `deleteById`
without writing any SQL. Methods we *do* write are **derived queries**: Spring Data parses the
method name (`findByEmail` → `SELECT … WHERE email = ?`).

### 6.10 `pet/service/PetService` — the brain

```java
@Service
public class PetService {
    private final PetRepository petRepository;                    // constructor injection
    public PetService(PetRepository r) { this.petRepository = r; }

    @Transactional(readOnly = true)                              // one DB transaction
    public Page<PetSummary> listPets(Pageable p) {
        return petRepository.findAll(p).map(PetService::toSummary);
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

`Page.map(...)` transforms every element of the page: `Page<Pet>` → `Page<PetSummary>`, one
line, no extra SQL.

### 6.11 `pet/controller/PetController` — thin HTTP layer

```java
@GetMapping                       // GET /api/v1/pets?page=0&size=20
public Page<PetSummary> listPets(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
    int safeSize = Math.min(size, 50);          // hard cap: never trust the client
    int safePage = Math.max(page, 0);
    return petService.listPets(PageRequest.of(safePage, safeSize, Sort.by(DESC, "createdAt")));
}
```

Why the cap? Without it, `?size=1000000` makes the database return a million rows and ships
megabytes of JSON. Unbounded pagination is a classic denial-of-service vector.

### 6.12 `pet/PetNotFoundException`, `user/EmailAlreadyRegisteredException`

Domain exceptions: they describe *what went wrong* in business language and know nothing about
HTTP. `GlobalExceptionHandler` decides how each one becomes a status code. Extending
`RuntimeException` means callers aren't forced to write `try/catch` and transactions roll back
automatically.

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
hand-written filters**. Four beans:

| Bean | Job |
|---|---|
| `SecretKey` | the HMAC key, decoded from Base64 in `application.yaml` (≥ 256 bits required for HS256) |
| `JwtEncoder` | signs tokens when someone logs in (used by `AuthService`) |
| `JwtDecoder` | verifies signature/algorithm/expiry on every protected request (used by Spring Security) |
| `JwtAuthenticationConverter` | maps the `role` claim → `ROLE_USER` / `ROLE_SELLER` authorities |

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
return LoginResponse.bearer(issueToken(user), …);
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
| `405` | wrong HTTP method for that URL | `POST /api/v1/health` |
| `409` | valid request, clashes with existing state | email already registered |
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
| `permitAll("/error")` | real error statuses stay visible |
| `permitAll(GET, "/api/v1/pets", "/api/v1/pets/**")` | anyone can **browse** |
| `permitAll(POST, "/api/v1/auth/**")` | you must be able to register without a token |
| `anyRequest().authenticated()` | everything else needs credentials |

Note the **`HttpMethod.GET`**: anonymous users may *look* at pets, but `POST /api/v1/pets`
(once it exists) stays locked. Browsing is public; acting is not.

Session handling is `STATELESS` — no cookies, no server-side session. That is why CSRF is
disabled (CSRF protection exists to protect cookie-based sessions) and why Step 6 uses an
`Authorization: Bearer <token>` header.

### Tokens, end to end

```
POST /api/v1/auth/login  {"email": "...", "password": "..."}
   → 200 {"accessToken":"eyJhbGci…","tokenType":"Bearer","expiresInSeconds":1800}

Every later request:
   GET /api/v1/users
   Authorization: Bearer eyJhbGci…
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

---

## 10. Database, entities and migrations

### Tables (from `V2__create_users_and_pets.sql`)

```
users:  id, email(UNIQUE), password_hash, display_name, phone?, role, verified, created_at
pets:   id, seller_id → users(id), title, species, breed?, age_months, price,
        description?, city, province, status, created_at
```

Indexes on `pets`: `(status, created_at DESC)`, `(species, breed)`, `(province, city)`,
`(price)`, `(seller_id)`, and a trigram index on `title` for search.

### Migrations

| File | What it did |
|---|---|
| `V1__init.sql` | enabled the `pg_trgm` extension (fuzzy text search) |
| `V2__create_users_and_pets.sql` | created both tables + indexes |
| `V3__seed_sample_pets.sql` | 1 demo seller + 3 demo pets |

**Rules:**

* ✅ Add new migrations as `V4__….sql`, `V5__….sql`, …
* ❌ **Never edit a migration that already ran.** Flyway stores a checksum of each file; changing
  it makes startup fail with `Migration checksum mismatch`.
* Flyway runs pending migrations automatically at startup (you'll see `Migrating schema "public"
  to version "4 - …"`).

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
| `PetControllerTest` (`@WebMvcTest`) | web layer only | ❌ (service is mocked) | status codes, JSON shape, 404 problem format, security whitelist |
| `PetRepositoryTest` (`@DataJpaTest`) | JPA layer | ✅ (real Docker Postgres, rolled back after each test) | queries, pagination counts, `Optional` behaviour, status filtering |
| `PetServiceTest` (plain Mockito) | nothing (no Spring) | ❌ repository is a mock | business rules: the service really asks for `ACTIVE` pets |
| `AuthServiceTest` (plain Mockito, real BCrypt) | nothing | ❌ repository is a mock | login rules: correct password, wrong password, unknown email, legacy hashes, email normalisation |
| `AuthControllerTest` (`@WebMvcTest`) | web layer | ❌ (service mocked) | login is public, returns a Bearer token, 401 problem detail, 400 validation |
| `PawzaarApiApplicationTests` (`@SpringBootTest`) | everything | ✅ | the whole context starts |

Current total: **20 tests**, all green with `mvn test`.

```bash
.\mvnw.cmd test                                            # all 8 tests
.\mvnw.cmd test "-Dtest=PetControllerTest"                 # one class
.\mvnw.cmd test "-Dtest=PetRepositoryTest#findByIdWithRandomUuidIsEmpty"   # one method
```

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
403, empty body                  security chain (stage 1)       SecurityConfig whitelist?
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
| **403, not 401, before auth exists** | no authentication mechanism to challenge against | expected until Step 6.4 |
| **Delegating encoder needs a prefix** | `IllegalArgumentException: each password must have a password encoding prefix` → login answered **500** instead of 401 | store `"{bcrypt}$2a$10$..."`, and set a fallback encoder (`setDefaultPasswordEncoderForMatches`) for hashes written before the change |
| **Hand-written "dummy" BCrypt hash** | a copied hash string made `matches()` throw instead of returning `false` | generate the timing-protection hash with the injected encoder at startup |
| **Spring Security 7 API changes** | `DelegatingPasswordEncoder` has no 3-arg constructor with a fallback encoder any more | check the real class with `javap` before trusting an older example |
| **IntelliJ green, Maven red** | the IDE does its own Lombok processing | trust `mvn test`, not only the IDE. |
| **Repeated `maven.compiler.proc` flag eaten** | `-D...` without quotes in PowerShell | quote `-D` arguments: `"-Dmaven.compiler.proc=full"`. |

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
| 6.3 | **Token decision (JWT) + `POST /api/v1/auth/login`** | ✅ done |
| 6.4 | Token verification wired in (403 → 401 confirmed) | ✅ done |
| 7 | Seller actions: `POST/PATCH/DELETE /pets` with ownership authorization, `GET /pets/mine` | ⏳ |
| 8 | Portfolio polish: GitHub Actions CI, env-var secrets, OpenAPI docs, Dockerfile | ⏳ |

---

*Maintained by the Pawzaar project. If something in this document is wrong or out of date,
that's a bug — fix the code and this file together.*
