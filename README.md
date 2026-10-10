# Pawzaar API

[![CI](https://github.com/MjTuplano18/Pawzaar-Pet-Marketplace/actions/workflows/ci.yml/badge.svg)](https://github.com/MjTuplano18/Pawzaar-Pet-Marketplace/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-25-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-blue)

A Philippines-focused pet marketplace API. Buyers browse pets for sale; sellers post listings.

This is a learning + portfolio project built one vertical slice at a time, with a strong emphasis
on **industry standards**: layered architecture, DTOs instead of exposed entities, one error format,
stateless JWT auth with refresh-token rotation, role-based authorization, automated tests, and CI.

> **New to the codebase?** Read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — it explains every
> package, the request flow, and the reasoning behind each design decision.

---

## Features

- **Public browsing** — paginated, `ACTIVE`-only listings (hidden and sold pets never leak).
- **Search & filtering** — optional `species`, `province`, `city`, `breed` (case-insensitive
  contains), price/age ranges, and a sort allowlist (`createdAt`, `price`, `ageMonths`).
- **Seller flow** — create, replace, and soft-delete your own listings, with ownership enforced in
  the service layer. Posting your first listing promotes your account to `SELLER`.
- **Authentication** — registration + login issuing a short-lived **access JWT** and a long-lived,
  **rotating refresh token**; generic 401s that never reveal whether an email exists.
- **Email verification** — registration sends a single-use, expiring link; `POST /auth/verify-email`
  marks the account verified. The default sender **logs** the link in dev (no SMTP account needed);
  swap in a real `EmailSender` bean for production. Nothing is gated on the flag yet.
- **Authorization** — URL rules plus method-level `@PreAuthorize`: any authenticated user may post a
  listing, but only `SELLER`s may edit or delete, on top of a service-layer ownership check.
- **One error format** — every error is an RFC 9457 `application/problem+json` response.
- **Abuse protection** — the auth endpoints are rate limited per client IP (token bucket) and return
  **429** `problem+json` with a `Retry-After` header; spoofed `X-Forwarded-For` is ignored by default.
  Per-account caps (max listings per seller, max images per listing) return **409**.
- **Listing images** — owners upload JPEG/PNG/WebP photos, validated by declared type, size, **and
  magic bytes**; the cover appears on cards, all images on the detail page, and the bytes stream back
  with a cache header. Every upload is **re-encoded to strip EXIF/GPS metadata** and its **pixel
  dimensions are capped** (a decompression-bomb guard). Served from local disk behind an
  `ImageStorage` interface (S3/CDN-ready).
- **Observability & docs** — Actuator health probes (liveness/readiness), Swagger UI (dev/test only),
  and structured logging: every response carries an `X-Request-Id` header that is also stamped into the
  server logs, 5xx errors / storage failures / auth failures are logged (never passwords or tokens).
- **Quality gates** — 221 tests, a strict CORS allowlist, and a GitHub Actions pipeline that builds and
  tests every push; `.\verify-local.ps1` runs the same checks (plus a live smoke test) locally.

---

## Tech stack

| Layer | Choice |
|---|---|
| Language / build | Java 25, Maven (wrapper included) |
| Framework | Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator) |
| Database | PostgreSQL 17, Flyway migrations, `ddl-auto: validate` |
| Auth | Spring Security OAuth2 Resource Server, HS256 JWT + opaque refresh tokens |
| Docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, Mockito, MockMvc, real PostgreSQL for JPA slices |
| CI / deploy | GitHub Actions + `verify-local.ps1`, multi-stage Dockerfile |

---

## Getting started

### Prerequisites

- **JDK 25+** (`java -version`)
- **Docker** (for the local PostgreSQL container)

### 1. Start the database

```bash
docker compose up -d
```

Postgres is exposed on host port **5433** (not 5432, to avoid clashing with a local install).
pgAdmin is available at <http://localhost:5050>.

### 2. Run the API

The app requires an active profile (dev or prod) and a JWT secret. Copy the dev env file and start:

```powershell
# Windows PowerShell
Copy-Item .env.dev.example .env.dev   # first time only, then edit values
.\run-dev.ps1
```

Or with Maven directly (any OS), passing the profile and secret as environment variables:

```bash
export SPRING_PROFILES_ACTIVE=dev
export JWT_SECRET=...        # Base64, at least 256 bits
./mvnw spring-boot:run
```

The API starts on <http://localhost:8080>:

- Health check: <http://localhost:8080/api/v1/health>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

The `dev` profile seeds a sample seller and three listings automatically (see "Sample data" below);
credentials for that throwaway account live only in the dev-only migration file, never in this README.

### 3. Run the tests

```bash
./mvnw test
```

The JPA and `@SpringBootTest` slices need a real PostgreSQL on `localhost:5433`, so keep
`docker compose up -d` running (the CI pipeline starts an equivalent service).

For a **full local verification** — compile + all tests + package, the Docker image build, and a
live end-to-end smoke test of the running API — run the script that mirrors CI:

```powershell
.\verify-local.ps1
```

It needs Docker running, and it cleans up after itself (the live probe uses a throwaway account and
deletes it again, so your seed data is untouched). Skip phases with `-SkipDockerImage`, `-SkipLive`,
or `-SkipTests`.

---

## API overview

All routes are versioned under `/api/v1`.

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/health` | public | liveness probe |
| `POST` | `/auth/register` | public | create an account (201 + `Location`) |
| `POST` | `/auth/login` | public | issue an access + refresh token pair |
| `POST` | `/auth/refresh` | public\* | exchange a refresh token for a new pair (rotation) |
| `POST` | `/auth/logout` | public\* | revoke a refresh token (204) |
| `POST` | `/auth/verify-email` | public | consume the emailed token, mark the account verified (204) |
| `POST` | `/auth/verify-email/resend` | bearer | send a fresh verification link (204) |
| `GET` | `/pets` | public | paginated, filterable, sortable `ACTIVE` listings |
| `GET` | `/pets/{id}` | public | one `ACTIVE` listing (404 otherwise) |
| `POST` | `/pets` | USER/SELLER | create a listing (201 + `Location`) |
| `PUT` | `/pets/{id}` | SELLER (owner) | replace your listing |
| `DELETE` | `/pets/{id}` | SELLER (owner) | soft-delete your listing (204) |
| `POST` | `/pets/{id}/images` | USER/SELLER (owner) | upload an image (multipart, 201 + `Location`) |
| `DELETE` | `/pets/{id}/images/{imageId}` | USER/SELLER (owner) | remove an image (204) |
| `GET` | `/pets/{id}/images/{imageId}` | public | fetch image bytes (`image/*`) |
| `GET` | `/me/pets` | bearer | your listings, any status |
| `GET` | `/actuator/health` | public | readiness/liveness for the host |

\* Authenticated by the refresh token in the request body, not by an access token.

The `auth` endpoints are rate limited per client IP (default 20 requests/minute). Exceeding the limit
returns **429** `problem+json` with a `Retry-After` header.

> **Rate limiting behind a proxy (H2).** Behind Render / Railway / a load balancer, raw socket
> connections come from the *proxy's* IP, so "per client IP" would silently become "per proxy" —
> one shared 20/minute bucket that can lock out every user at once. The `prod` profile therefore
> sets `server.forward-headers-strategy=native` so Tomcat applies `X-Forwarded-For` to the remote
> address and the limiter keys on the real client. This is only safe because those platforms
> **overwrite** the header before forwarding; if your host passes the header through untouched,
> keep the env knob `RATE_LIMIT_TRUST_FORWARDED_FOR=false` (the default) and understand you are
> globally rate-limited instead.

> **Email verification (M4d).** Registering issues a single-use verification token (stored only as a
> SHA-256 hash, 24h TTL) and hands the link to an `EmailSender`. The default implementation is
> `LoggingEmailSender`: it writes the message to the log instead of delivering it, so local dev and
> tests need no mail account. Run with the `dev` profile (`pawzaar.email.log-body=true`) and copy the
> `...?token=...` link from the console; in production, supply your own `EmailSender` bean and
> `EmailConfig` backs off automatically. Bad, expired and already-used tokens all return **400**
> `problem+json`; `/auth/verify-email` is rate limited and `/auth/verify-email/resend` requires a
> bearer token.

List endpoints are paginated (`?page=0&size=20`) and the page size is hard-capped at **50**.

`GET /pets` also accepts optional filters — `species=DOG` (exact), `province=Bulacan` and
`city=Quezon%20City` (case-insensitive), `breed=retriever` (case-insensitive contains; `%` and `_`
are matched literally), `minPrice`/`maxPrice`, and `minAgeMonths`/`maxAgeMonths` — plus `sort`
(`createdAt`, `price`, `ageMonths`) and `order` (`asc`/`desc`, default `desc`). An unknown sort field
or direction, or a `min` greater than its `max`, returns **400** `problem+json`.

```
GET /api/v1/pets?species=DOG&maxPrice=15000&sort=price&order=asc
```

Listing images (`POST /pets/{id}/images`, multipart field `file`) accept `image/jpeg`, `image/png`,
and `image/webp` up to 5 MiB. The server inspects the actual bytes, not just the `Content-Type`
header, and answers **400**/**413**/**415** `problem+json` on bad input. Images are streamed back from
`GET /pets/{id}/images/{imageId}`.

A ready-made [Postman collection](postman/Pawzaar_API.postman_collection.json) covers health, pets
(including search filters), auth, the login/refresh flow, and listing images.

---

## How authentication works

```
POST /auth/login         → { accessToken (30 min), refreshToken (7 days) }
Authorization: Bearer …   on every protected request
POST /auth/refresh        → a NEW pair; the old refresh token is revoked (rotation)
POST /auth/logout         → revoke the refresh token
```

Access tokens are stateless JWTs (verified by signature, no database lookup, not revocable).
Refresh tokens are opaque random strings stored **only as a SHA-256 hash** so they can be rotated
and revoked. See
[`docs/ARCHITECTURE.md` §6.22](docs/ARCHITECTURE.md) for the full rationale.

---

## Project structure

```
src/main/java/com/pawzaar
├── common/     cross-cutting: one exception handler, paged response, health, rate limiting
├── config/     SecurityConfig, CorsConfig, JwtConfig, RateLimitConfig, PasswordEncoderConfig
├── pet/        feature slice: entity, DTOs, repository, service, controller
└── user/       feature slice: User, RefreshToken, DTOs, repositories, auth service/controller
```

Inside `pet/`, the `image/` sub-package holds the storage abstraction (`ImageStorage` /
`LocalImageStorage`), the byte-level validator, and the `PetImage` entity/repository.

Package-by-feature with layer sub-packages. The layers only point downward:
**Controller → Service (`@Transactional`) → Repository → PostgreSQL**. Entities never leave the
service; controllers return DTO records only.

---

## Roadmap

| Done | Next |
|---|---|
| Public browsing, seller CRUD, JWT + refresh tokens, email verification, role-based `@PreAuthorize`, search & filtering, auth rate limiting, listing images, CORS, tests, CI, Docker | Admin moderation & reporting |

The full plan lives in
[`Pawzaar — Spring Boot Learning & Build Roadmap.md`](<Pawzaar — Spring Boot Learning & Build Roadmap.md>).

---

## License

Not licensed for reuse yet — this is a personal portfolio project.
