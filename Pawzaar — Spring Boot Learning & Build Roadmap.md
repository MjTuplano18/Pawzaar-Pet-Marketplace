# Pawzaar — Spring Boot Learning & Build Roadmap

Oct 7, 2026 · @Mj

## The big picture

Build Pawzaar as one well-structured Spring Boot application (a modular monolith) in about 20 weeks at 8–10 hours a week, learning each Spring concept only when the next feature needs it. This assumes you already know core Java and OOP, and are new to Spring, SQL design, and deployment.

Four principles drive every decision below:

- **Monolith first, microservices never (for now).** One deployable app with clean internal modules is faster to build, easier to debug, and cheaper to host. Split only if measurements prove you must.
- **Vertical slices.** Finish one feature end to end (database, API, frontend, tests) before starting the next. A half-built chat feature teaches you nothing; a finished "Post a pet" flow teaches you everything.
- **Secure and measurable from day one.** Authentication, validation, and logging are part of Version 1, not a cleanup task. Optimize only after you measure.
- **Learn by building, with AI as a tutor.** You type the code, AI explains and reviews it.

By the end you will have practiced REST APIs, dependency injection, JPA and SQL, Spring Security with JWT, file uploads, caching, testing, Docker, and CI/CD, which is a strong portfolio story.

## Recommended stack

Your PDF stack is a good base. These upgrades add what a production-quality marketplace needs, and all of them are free. Check start.spring.io for the current stable Spring Boot and Java LTS versions when you begin.

| Layer | Choice | Why |
| --- | --- | --- |
| Language / build | Java 21+ (LTS), Maven | Maven has the most tutorials and Spring Initializr support |
| Framework | Spring Boot: Web, Data JPA, Security, Validation, Actuator | Covers API, database, auth, input checks, and health metrics |
| Database | PostgreSQL (Supabase or Neon free tier; Docker locally) | Relational data suits users, listings, chats, ratings |
| Migrations | Flyway | Versioned SQL scripts; never let Hibernate auto-create production tables |
| Auth | Spring Security + JWT (short-lived access token, refresh token in httpOnly cookie) | Stateless, standard, and a key skill to show |
| Image storage | Supabase Storage or Cloudflare R2 (S3-compatible) | Keeps files out of your database and server disk |
| Cache | Caffeine first, Redis later | In-process cache needs no extra service |
| Search | PostgreSQL indexes + full-text search (pg\_trgm) | Good enough for tens of thousands of listings |
| API docs | springdoc-openapi (Swagger UI) | Auto-generated docs; the frontend and you test with it |
| Testing | JUnit 5, Mockito, Testcontainers | Real PostgreSQL in tests, not fakes |
| Real-time chat (V2) | Spring WebSocket (STOMP) | Built in; no extra service |
| Frontend | React + Vite + TypeScript, React Router, TanStack Query | TypeScript catches API mismatches; TanStack Query handles caching and loading states |
| Hosting | Vercel (frontend), Render or Railway (backend), Docker image | Free tiers have limits and cold starts, and they can change |
| CI/CD | GitHub Actions | Free for public repositories; runs tests on every push |
| Monitoring | Actuator, Sentry free tier, UptimeRobot | Errors and downtime alerts at no cost |

Skip for now: Kubernetes, microservices, Kafka, Elasticsearch, and a separate search service. Each adds weeks of work and solves problems you do not have yet.

## Architecture

Use a layered, package-by-feature modular monolith. Each feature (pets, users, chat) owns its controller, service, repository, entities, and DTOs, so modules can be understood and later extracted independently.

```text
com.pawzaar
├── common/          exceptions, error format, pagination, utilities
├── config/          SecurityConfig, CorsConfig, OpenApiConfig, StorageConfig
├── auth/            AuthController, AuthService, JwtService, refresh tokens
├── user/            User entity, UserController, UserService, UserRepository
├── pet/             Pet, PetImage, PetController, PetService, PetRepository
│   ├── dto/         PetCreateRequest, PetResponse, PetSummary, PetSearchCriteria
│   └── PetMapper    entity <-> DTO conversion
├── favorite/        (V2)
├── chat/            (V2) conversations, messages, WebSocket config
├── rating/          (V2)
├── report/          (V2) listing reports + moderation
└── storage/         StorageService interface + Supabase/R2 implementation
```

Rules that keep the codebase clean:

1. **Controllers are thin.** They parse the request, call one service method, and return a DTO. No business logic, no repositories.
2. **Services hold the rules.** Ownership checks, status changes, and `@Transactional` boundaries live here. This is where dependency injection and interfaces pay off.
3. **Repositories only talk to the database.** Use Spring Data JPA interfaces; write custom queries only when needed.
4. **Never return entities from the API.** Use request and response DTOs (Java records work well). This prevents leaking fields like password hashes and avoids lazy-loading errors.
5. **One error format.** A `@RestControllerAdvice` converts exceptions to RFC 9457 `ProblemDetail` responses (Spring supports this natively), so the frontend always receives the same error shape.
6. **Program to interfaces at boundaries.** `StorageService` and later `PaymentGateway` are interfaces, so you can swap Supabase for R2, or fake them in tests.
7. **Configuration by profile.** `application-dev.yml` and `application-prod.yml`; secrets come from environment variables, never from Git.

The drawing below shows how one request travels through the system.

&#91;embedded content: request flow · 6 layers, 3 supporting parts\]

A request is checked by the security filters, validated in the controller, decided in the service, and saved by the repository; error handling, caching, and image storage plug in beside the service.

## System design

Design the database first, then the API, because every other decision depends on them. Start with the MVP tables and add the Version 2 tables through new Flyway migrations.

### Database schema

| Table | Key columns | Notes |
| --- | --- | --- |
| users | id (UUID), email (unique), password\_hash, display\_name, phone, role, verified, created\_at | Roles: USER, SELLER, ADMIN. Never store plain passwords. |
| pets | id (UUID), seller\_id, title, species, breed, age\_months, sex, price (numeric 10,2), description, city, province, status, created\_at | Status: ACTIVE, SOLD, HIDDEN, PENDING\_REVIEW. Use species as an enum. |
| pet\_images | id, pet\_id, storage\_key, position, created\_at | Store the storage key, not a full URL. |
| favorites (V2) | user\_id, pet\_id, created\_at | Composite primary key (user\_id, pet\_id). |
| conversations, messages (V2) | conversation: pet\_id, buyer\_id, seller\_id. message: conversation\_id, sender\_id, body, sent\_at | One conversation per buyer and listing. |
| ratings (V2) | id, seller\_id, rater\_id, stars, comment, created\_at | One rating per buyer and seller. |
| reports (V2) | id, pet\_id, reporter\_id, reason, status, created\_at | Feeds an admin moderation queue. |
| refresh\_tokens | id, user\_id, token\_hash, expires\_at, revoked | Enables logout and token rotation. |

Key indexes for the MVP: `pets(status, created_at DESC)`, `pets(species, breed)`, `pets(province, city)`, `pets(price)`, `pets(seller_id)`, and a trigram index on `pets(title)` for text search.

### REST API (MVP)

| Method and path | Access | Purpose |
| --- | --- | --- |
| POST /api/auth/register, /login, /refresh, /logout | Public | Account and token lifecycle |
| GET /api/pets?species=&breed=&province=&minPrice=&maxPrice=&page=&size= | Public | Search and filter, paginated |
| GET /api/pets/{id} | Public | Pet details with images and seller summary |
| POST /api/pets | Logged in | Create a listing |
| PUT, DELETE /api/pets/{id} | Owner only | Edit or remove own listing |
| POST /api/pets/{id}/images | Owner only | Upload photos |
| GET /api/me/pets | Logged in | My Listings |

Version the API from the start (`/api/v1/...`), use plural nouns, return correct status codes (201 on create, 204 on delete, 400/401/403/404/409/422 for errors), and always paginate list endpoints.

### Image upload flow

1. MVP: the client sends multipart images to Spring. Enforce a maximum count (say 5), a maximum size (say 5 MB each), and allowed types (JPEG, PNG, WebP).
2. The server checks the real file type from its content, re-encodes it to WebP, and creates a thumbnail.
3. The server uploads to storage under a random key, and saves only the key in `pet_images`.
4. Version 2 upgrade: switch to presigned upload URLs so the file goes straight from the browser to storage and never loads your small free server.

### Search

Start with JPA Specifications (or Querydsl) to combine optional filters, and PostgreSQL full-text search or `pg_trgm` for keyword matching. This handles tens of thousands of listings comfortably. Move to a dedicated search engine only if query times exceed your target after indexing.

### Scaling path (only when measurements demand it)

1. Make the app stateless, so you can run several copies behind a load balancer.
2. Serve images through a CDN.
3. Add Redis for shared caching and rate limits across instances.
4. Add a read replica for heavy search traffic.
5. Move slow work (emails, image processing) to a background queue.

## Security

A pet marketplace attracts scammers and fake sellers, so treat security and trust as core features. Use the OWASP Top 10 and the OWASP API Security Top 10 as your checklist, and review it before each release.

| Risk | Defense in Pawzaar |
| --- | --- |
| Stolen or weak passwords | Hash with BCrypt or Argon2 (Spring's `PasswordEncoder`); minimum length rule; never log passwords; add email verification and login throttling |
| Token theft | Access token valid for about 15 minutes, kept in memory on the frontend; refresh token in an `HttpOnly`, `Secure`, `SameSite` cookie, stored hashed, rotated on every use, revocable on logout |
| Broken access control (users editing others' listings) | Check ownership in the service layer on every write, and use `@PreAuthorize` for roles. Write a test that proves user A gets 403 on user B's listing. |
| Injection | Use JPA and parameterized queries only; never build SQL by string concatenation |
| Bad input | Bean Validation (`@Valid`, `@NotBlank`, `@Size`, `@Positive`) on every request DTO; whitelist sortable fields; cap page size at 50 |
| Malicious uploads | Check type from file content, limit size and count, re-encode images, random storage keys, never serve uploads from your app domain |
| Abuse and brute force | Rate limit login, register, and posting (Bucket4j or a gateway rule); CAPTCHA on registration if spam appears |
| Cross-site attacks | Strict CORS allowlist (your Vercel domain only); avoid storing tokens in localStorage; escape user text on display; set security headers (CSP, `X-Content-Type-Options`, HSTS) |
| Secret leaks | Environment variables or the host's secret store; `.gitignore` for `.env`; enable GitHub secret scanning; rotate keys if exposed |
| Vulnerable dependencies | Enable Dependabot and run an OWASP Dependency-Check or similar scan in CI |
| Information leaks | Never return stack traces or entity internals; return generic auth errors ("invalid email or password"); use UUIDs instead of sequential IDs |
| Seller privacy | Hide phone and email until a user is logged in; prefer in-app chat over exposing contact details |
| No evidence after incidents | Audit log for logins, listing edits, reports, and admin actions |

Other essentials:

- Use HTTPS everywhere (Vercel and Render provide it automatically) and set the `Secure` flag on cookies.
- Keep stateless sessions (`SessionCreationPolicy.STATELESS`). If you use cookie-based refresh, protect refresh and logout endpoints against CSRF.
- Collect only the personal data you need and publish a privacy policy. The Philippines' Data Privacy Act of 2012 (RA 10173) applies once you collect users' personal information, so confirm current requirements before launching publicly.
- Add a basic admin role early, so you can hide listings and ban users quickly.

## Performance and optimization

Measure first, then optimize the slowest thing. Set simple targets for the MVP: listing search under 300 ms at the 95th percentile on your hosting, home page loading in under 3 seconds on a mid-range phone, and no page over 1 MB of images above the fold.

| Area | Practice | Why it matters |
| --- | --- | --- |
| Database indexes | Index every column you filter or sort by; check slow queries with `EXPLAIN ANALYZE` | The biggest single speed gain for search pages |
| N+1 queries | Return DTO projections or use `@EntityGraph` / `JOIN FETCH` for list pages; turn on SQL logging in dev and count queries per request | The most common JPA performance bug |
| Pagination | Always page results; use keyset pagination ("after this created\_at and id") once offset paging gets slow | Keeps response size and memory flat |
| Open session | Set `spring.jpa.open-in-view=false` | Stops hidden lazy queries in controllers |
| Connection pool | Keep HikariCP small (about 5 to 10) on free databases, which have connection limits | Avoids "too many connections" errors |
| Caching | Cache rarely changing lookups (breeds, provinces) with `@Cacheable` and Caffeine; set an expiry; evict on change | Cuts repeated reads. Do not cache listings until you measure a need. |
| HTTP | Enable response compression, ETag or `Cache-Control` for public GETs | Smaller, faster responses |
| Images | Convert to WebP, generate thumbnails, lazy-load, set width and height, serve through a CDN | Images are usually 80% or more of page weight |
| Frontend | Route-level code splitting, TanStack Query caching, debounced search input, skeleton loaders | Feels fast even on slow connections |
| Background work | Use `@Async` for emails and image processing | Keeps requests short |
| Cold starts | Free hosts sleep after inactivity; use a health check ping and show a loading state | Free tiers trade speed for price |

How to measure:

- Expose metrics with Spring Boot Actuator and watch request time, error rate, and database pool use.
- Load test key endpoints with k6 before release (search, list, login).
- Run Lighthouse on the frontend and fix anything under 90 for performance.
- Log a request ID with every request so you can trace a slow call.

## Spring Boot learning path

Learn each concept just before the feature that needs it. For each row: read the concept (30 to 60 minutes), build a tiny throwaway example, then apply it to Pawzaar.

| Week | Learn | Pawzaar feature that uses it |
| --- | --- | --- |
| 1 | Spring Initializr, project structure, `@SpringBootApplication`, `application.yml`, profiles, dependency injection and the IoC container | Project setup, health endpoint |
| 2 | `@RestController`, `@RequestMapping`, path and query parameters, `ResponseEntity`, DTOs as records | First read-only pet endpoints |
| 3 | Docker Compose, PostgreSQL, Spring Data JPA, `@Entity`, relationships (`@ManyToOne`, `@OneToMany`), Flyway | Users and pets tables, repositories |
| 4 | Service layer, `@Transactional`, constructor injection, Bean Validation, `@RestControllerAdvice`, `ProblemDetail` | Create, update, delete listings with clean errors |
| 5 | Spring Security basics: filter chain, `PasswordEncoder`, `UserDetailsService` | Registration and login |
| 6 | JWT, refresh tokens, authorization (`@PreAuthorize`), CORS | Protected endpoints, ownership checks |
| 7 | Pagination (`Pageable`), sorting, JPA Specifications, indexes, `EXPLAIN` | Search and filters |
| 8 | Multipart uploads, interfaces for storage, external HTTP APIs | Photo upload to Supabase or R2 |
| 9 | Unit tests (Mockito), `@WebMvcTest`, `@DataJpaTest`, Testcontainers | Test suite for pets and auth |
| 10 | Actuator, logging, Dockerfile, GitHub Actions, deployment | MVP live on the internet |
| 11 to 12 | Caching (`@Cacheable`), N+1 fixes, rate limiting | Performance pass |
| 13 to 14 | WebSocket and STOMP, message persistence | Buyer and seller chat |
| 15 to 16 | Many-to-many tables, aggregate queries, role-based admin endpoints | Favorites, ratings, reports, verification |
| 17 to 20 | Scheduling (`@Scheduled`), events, file processing, external APIs | Vaccination records, health profile, map data |

Recommended free resources:

- The official guides at spring.io/guides (short, task-based, always current)
- The Spring Boot and Spring Security reference documentation at docs.spring.io
- Spring Initializr at start.spring.io for generating projects
- Baeldung articles to look up one specific topic at a time

Habits that make you learn faster:

1. Type the code yourself; never paste a whole generated feature.
2. Before running anything, predict what will happen.
3. When something fails, read the stack trace from the first line that mentions your package.
4. Write one test per feature, so you learn how the framework behaves.
5. Keep a `LEARNING.md` file with one sentence on what you learned each week.

## Build plan

The plan has four phases. Do not start a phase until the previous one meets its checklist.

### Phase 0: Setup (week 1)

- [ ] Create a GitHub repository with two folders, `backend` and `frontend` (a monorepo is simplest for one person)
- [ ] Generate the backend at start.spring.io: Maven, Java 21+, dependencies Web, Validation, Data JPA, PostgreSQL Driver, Security, Actuator, Flyway
- [ ] Write `docker-compose.yml` with PostgreSQL, and connect Spring to it using environment variables
- [ ] Add a `GET /api/v1/health` endpoint and confirm it works in the browser
- [ ] Scaffold the frontend with `npm create vite@latest` (React + TypeScript)
- [ ] Add a README with the run instructions and a GitHub Actions workflow that builds the backend

### Phase 1: MVP (weeks 2 to 10)

**Weeks 2 to 4: Listings without login.** Create the `pets` and `users` tables with Flyway. Build `Pet` entity, `PetRepository`, `PetService`, `PetController`, and response DTOs. Seed 20 sample pets. Add global error handling and Swagger UI. Frontend: home page showing pet cards, and a details page.

**Weeks 5 to 6: Authentication.** Register and login endpoints, BCrypt hashing, JWT filter, refresh token rotation, protected routes in React, and an auth context. Add ownership rules to the service layer and write tests proving other users cannot change your listing.

**Weeks 7 to 8: Posting and searching.** Post-a-pet form with validation, My Listings page, edit and delete. Then search and filters (location, breed, price, age) with JPA Specifications, pagination, and the database indexes from the system design section.

**Weeks 9 to 10: Images, tests, deployment.** Photo upload through `StorageService`, WebP conversion and thumbnails, limits and file checks. Add controller, service, and repository tests. Containerize with a multi-stage Dockerfile, deploy the database, backend, and frontend, and configure production profiles and secrets.

MVP done when:

- [ ] A new user can register, log in, post a pet with photos, and see it in search
- [ ] A user cannot edit or delete anyone else's listing (tested)
- [ ] All list endpoints are paginated and the main filters use indexes
- [ ] Tests run in GitHub Actions on every push
- [ ] The app is live on a public URL, and no secrets are in Git

### Phase 1.5: Hardening (weeks 11 to 12)

- [ ] Rate limiting on login, register, and post endpoints
- [ ] N+1 check on every list endpoint and Caffeine cache for breeds and locations
- [ ] Security headers, CORS allowlist, and an OWASP checklist review
- [ ] Load test with k6 and record the before and after numbers in your README
- [ ] Sentry and uptime monitoring enabled

### Phase 2: Marketplace features (weeks 13 to 16)

1. **Favorites** (simple many-to-many, a good warm-up)
2. **Report listing** with a reason, a status, and an admin review list
3. **Seller ratings** (one rating per buyer and seller, average stored or computed with a query)
4. **Buyer and seller chat** with WebSocket and STOMP, saved messages, unread count, and authorization on who can join which conversation
5. **Seller verification** as a manual admin approval flow with a verified badge

Phase 2 done when each feature has tests, admin tools exist for reports and verification, and chat refuses unauthorized users.

### Phase 3: Advanced features (weeks 17 and beyond)

Add one at a time, in this order of value: vaccination records and pet health profile, adoption and rehoming listings, map and location features, pet supplies, then transport and services. Only add payments last, because disputes, refunds, fraud, and compliance multiply the complexity.

### Weekly rhythm

- Mon: pick one small slice of the feature and write it as 3 to 5 checklist items
- Tue to Thu: build it, asking AI for explanations rather than full solutions
- Fri: write tests, update the README and `LEARNING.md`, commit and push
- Weekend: optional, review what confused you and re-read the Spring docs on it

## Testing, CI/CD, and deployment

Automate the checks so every push proves the app still works.

| Test type | Tool | What it covers |
| --- | --- | --- |
| Unit | JUnit 5 + Mockito | Service rules (ownership, status changes, validation of business logic) |
| Web layer | `@WebMvcTest` + MockMvc | Status codes, validation errors, security rules per endpoint |
| Repository | `@DataJpaTest` + Testcontainers (PostgreSQL) | Queries, filters, indexes, Flyway migrations on a real database |
| Integration | `@SpringBootTest` + Testcontainers | Register, log in, post a pet, search it |
| Frontend | Vitest + React Testing Library | Forms and key components |
| End to end (later) | Playwright | Full user journey on the deployed site |

Aim for tests on every rule that could hurt a user (permissions, ownership, validation), not for a coverage percentage.

**CI pipeline (GitHub Actions), on every push and pull request:**

1. Check out the code and set up Java and Node
2. Run backend tests and build the jar
3. Run frontend lint, tests, and build
4. Run the dependency vulnerability scan
5. On the main branch only: build the Docker image and deploy

**Deployment checklist:**

- [ ] Multi-stage Dockerfile (build with Maven, run on a slim JRE image as a non-root user)
- [ ] Separate `dev` and `prod` profiles; all secrets as environment variables
- [ ] Flyway runs migrations on startup; `spring.jpa.hibernate.ddl-auto=validate` in production
- [ ] Health check endpoint wired to the host and an uptime monitor
- [ ] Database backups enabled (check what your free tier provides, and export manually if it provides none)
- [ ] Structured logs with request IDs; never log tokens or passwords
- [ ] A staging environment before production once you have real users

Git habits: one short-lived branch per feature, small commits with clear messages, a pull request even when working alone, and merge only when CI is green.

## Responsible marketplace and trust

Build trust and safety features into the product rules and code from the start, not as a later add-on. They also differentiate Pawzaar from social media pet groups.

**Product rules to implement**

- **Prohibited listings.** Block wildlife and protected species, animals that are too young to leave their mother (set a minimum age by species), and listings with misleading or stolen photos. Show clear rules in the posting form.
- **Required fields.** Species, breed (or "mixed"), age, sex, vaccination status, and location, so buyers have the information to make a safe decision.
- **Seller tiers.** Unverified (limited active listings, new-seller label), verified (ID or contact check by an admin, badge), and professional breeder or business (higher limits, subscription).
- **Reports and moderation.** One-tap report on every listing, an admin queue, and the ability to hide a listing, warn, or ban a seller. Track repeat offenders.
- **Scam prevention.** Warning banners about paying deposits to unverified sellers, advice to meet in public and see the animal in person, and a cap on how fast new accounts can post.
- **Duplicate and fake-photo checks.** Start with manual review of new sellers' first listings; consider image hashing later.
- **Adoption and rehoming.** Keep these separate from sales, with an application step and no fees that resemble hidden sales.
- **Records.** Optional vaccination and health records, with documents uploaded privately and shown only to a buyer the seller approves.

**Legal and compliance notes (verify with current texts or a lawyer before a public launch; this is not legal advice)**

- Philippine animal-related laws that are likely relevant include the Animal Welfare Act (RA 8485, as amended), the Anti-Rabies Act (RA 9482), and the Wildlife Resources Conservation and Protection Act (RA 9147). Check them for rules on cruelty, vaccination, and trade in wildlife.
- The Data Privacy Act (RA 10173) covers the personal data you collect. Add a privacy policy, terms of service, and a way for users to request deletion.
- Write terms that state Pawzaar is a listing platform and that transactions are between users, until you add payments.
- If you charge fees, check business registration and tax requirements. A student portfolio project can stay free and non-commercial until you decide to launch for real.

The monetization ideas in your PDF (featured listings, subscriptions, business ads) fit well after Phase 2, once there is real traffic and trust features. Building them as a separate `billing` module later keeps the core clean.

## Using AI as a tutor, and your first step

Use AI to explain, review, and unblock you, and keep the typing and decisions yours. These prompt patterns work well:

```text
Explain: "Explain @Transactional like I know Java but not Spring. Give a 10-line example and one common mistake."
Hint: "I'm stuck on X. Give me a hint only, not the solution."
Review: "Here is my PetService. Review it for bugs, security issues, and Spring best practices. Don't rewrite it."
Debug: "Here is the stack trace and the code. Explain what the error means before suggesting a fix."
Quiz: "Quiz me on JPA relationships with 5 questions, one at a time."
Step mode: "I'm a beginner in Spring Boot. Give me only Step 1 of the Pet Marketplace and wait until I say done."
```

**Start today (about 2 hours):**

1. Open start.spring.io and generate a Maven project, Java 21 or newer, with these dependencies: Spring Web, Validation, Spring Data JPA, PostgreSQL Driver, Spring Security, Spring Boot Actuator, Flyway Migration.
2. Open it in IntelliJ IDEA Community, and run the application once. Expect Spring Security to generate a temporary password and lock every endpoint; that is normal for now.
3. Add a `HealthController` with a `GET /api/v1/health` endpoint returning `{"status":"ok"}`, and temporarily permit it in a small `SecurityConfig`.
4. Push the project to GitHub with a README.
5. Then tell your AI tutor: "Step 1 is done. Give me Step 2: Docker Compose with PostgreSQL and my first Flyway migration."

Commit to this order: setup, listings without login, authentication, posting and search, images, tests, deployment. Everything else in this document builds on that spine.

## Backend readiness checklist

Work through this list in three passes: build it in now, finish it before the public launch, and add it only when traffic demands. Items marked done already exist in your code as of 9 October 2026.

### Pass 1: build it in now (while the backend is small)

**Structure and API quality**

- [x] Controller, service, repository layers with constructor injection
- [x] DTO records; entities never returned from the API
- [x] One error format with `ProblemDetail`
- [x] Page size capped at 50, fixed sort column
- [x] Flyway migrations with `ddl-auto: validate`
- [x] Public queries filter on `status = ACTIVE` (hidden and sold pets must not leak)
- [x] Every request DTO has Bean Validation (`@NotBlank`, `@Size`, `@Positive`) and every controller uses `@Valid`
- [x] Handle bad input for all standard errors (bad UUID, wrong method, malformed JSON) in the same ProblemDetail shape
- [x] Wrap list responses in a stable page record instead of returning `Page` directly
- [x] API documented with springdoc-openapi (Swagger UI)

**Security basics**

- [x] Stateless sessions, CSRF off for token auth, default deny (`anyRequest().authenticated()`)
- [x] Public endpoints listed explicitly; only `GET` is public for pets
- [x] Delegating password encoder (`{bcrypt}` prefix) so the algorithm can be upgraded
- [x] Passwords never logged, never returned in any response or exception message
- [x] Ownership check in the service layer on every update and delete (user A gets 403 on user B's listing), with a test
- [x] Return 401 (not 403) for anonymous requests to protected URLs
- [x] Generic login error ("invalid email or password"); no hint about which part was wrong
- [ ] Strict CORS allowlist (your frontend domain only), never `*` with credentials
- [ ] Request size limits set (`spring.servlet.multipart.max-file-size`, `max-request-size`, Tomcat max post size)
- [x] Sensitive actuator endpoints not exposed; only health (and later metrics) behind authentication

**Data and database**

- [x] UUID primary keys, `NUMERIC` for money, constraints in SQL
- [ ] Index on every filter and sort column, checked with `EXPLAIN ANALYZE`
- [ ] Foreign key from `pets.seller_id` to `users.id` is in SQL; map the relationship in JPA when login is built
- [ ] Seed data moved to a dev-only migration location before any deployment
- [x] `updated_at` column and optimistic locking (`@Version`) on listings
- [ ] Connection pool size set deliberately (see load balancing below)

**Configuration and secrets**

- [x] `application-dev.yaml` and `application-prod.yaml` profiles
- [ ] Database password, JWT secret, and storage keys come from environment variables, not Git
- [x] `.gitignore` covers `target/`, `.env`, and local config; GitHub secret scanning turned on
- [x] JWT signing key is at least 256 bits, generated randomly, and rotatable

### Pass 2: finish before the public launch

**Security hardening**

- [ ] Rate limiting on login, register, post-listing, and report endpoints (Bucket4j), keyed by IP and by user
- [ ] Account lockout or progressive delay after repeated failed logins
- [ ] Refresh tokens stored hashed, rotated on use, revoked on logout and password change
- [ ] Email verification before a user can post listings
- [ ] Upload checks: type from file content (not extension), size and count limits, re-encode images, random storage keys, files served from a separate domain
- [ ] Security headers: HSTS, `X-Content-Type-Options`, `Content-Security-Policy`, `X-Frame-Options`
- [ ] HTTPS enforced; `Secure`, `HttpOnly`, and `SameSite` flags on cookies
- [ ] Seller phone and email hidden from anonymous users
- [ ] Audit log for logins, listing edits, reports, and admin actions
- [ ] Dependabot and a dependency vulnerability scan in CI
- [ ] Review against the OWASP Top 10 and OWASP API Security Top 10 (broken object-level authorization is the most common API bug)

**Performance and reliability**

- [ ] No N+1 queries on any list endpoint (turn on SQL logging and count queries per request)
- [ ] `spring.jpa.open-in-view=false` (already set)
- [ ] Timeouts on every outbound call (storage, email) and on database queries
- [ ] Slow work (image processing, email) moved off the request thread with `@Async` or a queue
- [ ] Graceful shutdown enabled (`server.shutdown=graceful`) so deploys don't cut requests off
- [ ] Compression enabled for JSON responses
- [ ] Load test with k6 on search, list, and login; record p95 latency and error rate

**Observability**

- [ ] Structured logs with a request ID on every line; no tokens, passwords, or full personal data
- [x] Actuator health with liveness and readiness probes
- [ ] Error tracking (Sentry free tier) and an uptime monitor with alerts
- [ ] Metrics: request time, error rate, database pool usage, JVM memory

**Testing and delivery**

- [ ] Unit tests for service rules; `@WebMvcTest` for status codes and security; Testcontainers for repositories and migrations
- [x] Security tests: anonymous gets 401, wrong owner gets 403, hidden pet gets 404
- [ ] CI runs tests and builds on every push; main branch deploys only when green
- [ ] Multi-stage Dockerfile, non-root user, small JRE base image
- [ ] Database backups enabled and a restore tested at least once

### Pass 3: add only when traffic demands it

**Caching**

Start with no cache, measure, then add the smallest cache that fixes a measured problem.

| What | Where | Rule |
| --- | --- | --- |
| Breeds, provinces, other lookup lists | Caffeine with `@Cacheable` | Safe to cache for hours; evict when the data changes |
| Public listing pages | Short HTTP cache (`Cache-Control: max-age=30`) or Redis | Only if search load proves it; accept up to 30 seconds of staleness |
| Listing details | Usually do not cache | Price and status changes must show up quickly |
| Anything per user (My Listings, favorites) | Do not cache shared | One user's data must never reach another user |
| Images | CDN with long cache lifetimes and versioned file names | Biggest bandwidth saving |

- [ ] Every cache has a size limit and an expiry
- [ ] Every write path that changes cached data evicts or updates it
- [ ] Cache hit rate is measured; a cache with a low hit rate is removed
- [ ] If you run more than one app instance, move shared caches to Redis (Caffeine is per instance)

**Load balancing and horizontal scaling**

You do not need a load balancer until one instance can no longer handle the traffic, or you need deploys with no downtime. Your host (Render, Railway) usually provides one when you add instances. What you must do first is make the app safe to run as several copies:

- [ ] App is stateless: no HTTP sessions, no files saved on local disk, no in-memory state that matters (JWT and external storage already satisfy this)
- [ ] Rate limits and caches that must be shared live in Redis, not in app memory
- [ ] Scheduled jobs (`@Scheduled`) run on one instance only (use ShedLock or a single job runner), or they will run several times
- [ ] Health endpoint reports readiness so the balancer only sends traffic to ready instances
- [ ] Connection pool math: total connections = instances × pool size, and it must stay under your database's connection limit (free tiers often allow only 20 to 60)
- [ ] Forwarded headers handled (`server.forward-headers-strategy=framework`) so the app sees the real client IP and HTTPS behind the balancer
- [ ] Database migrations run once, not on every instance at the same time
- [ ] Rolling deploys tested: start the new version, wait for readiness, then stop the old one

**Scaling the data layer**

- [ ] Read replica for heavy search traffic
- [ ] Keyset pagination once offset paging gets slow on deep pages
- [ ] Dedicated search engine only if indexed PostgreSQL search misses your latency target
- [ ] CDN in front of images and, if needed, public GET responses
- [ ] Message queue for background work (image processing, notifications) once `@Async` is not enough

### What to ignore for now

Kubernetes, microservices, Kafka, service meshes, and multi-region setups. Each one costs weeks and solves problems a student marketplace does not have. A single well-tested app on one instance with a managed PostgreSQL will handle thousands of daily users.
