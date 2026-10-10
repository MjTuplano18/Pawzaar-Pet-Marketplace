# Pawzaar — frontend

The React + TypeScript single-page app for the Pawzaar pet marketplace. It talks to the Spring Boot
API in the parent folder (`../`).

**Stack:** React 19, Vite, TypeScript, React Router, TanStack Query (wired up, ready for the listing
pages).

## What's built so far (auth slice)

- **Register** (`/register`) → creates the account and tells the user to check their email.
- **Log in** (`/login`) → stores the token pair and loads the profile.
- **Verify email** (`/verify-email?token=…`) → the page the emailed verification link points at.
- **Forgot password** (`/forgot-password`) → requests a reset link (always a generic success).
- **Reset password** (`/reset-password?token=…`) → sets a new password from the emailed link.
- **Profile** (`/profile`, protected) → shows the signed-in user via `GET /api/v1/me`, with a
  "resend verification" action.

Listing pages (browse/detail/create) are the next slice.

## Run it

You need the API running on `http://localhost:8080` (see `../README.md`). Then:

```bash
npm install
npm run dev
```

Open http://localhost:5173.

The dev server **proxies `/api` to `http://localhost:8080`** (see `vite.config.ts`), so the browser
makes same-origin requests and CORS never applies in development. That is why `VITE_API_URL` stays
empty locally.

### Seeing the emails

In development the API does not send real email — it **logs** the verification/reset link to its
console. Run the API with the `dev` profile (`pawzaar.email.log-body=true`) and copy the
`...?token=...` link from the console into the browser.

## Configuration

Copy `.env.example` to `.env` (optional in dev) and set:

| Variable | Dev | Prod |
|---|---|---|
| `VITE_API_URL` | empty (use the proxy) | the deployed API origin, e.g. `https://pawzaar-api.onrender.com` |

Anything prefixed with `VITE_` is embedded in the client bundle — **never put secrets in it.**

## How auth works here

- The **access token** is kept **in memory only** (lost on refresh, by design — it is short-lived).
- The **refresh token** is kept in **`localStorage`** under `pawzaar.refreshToken`.
- Every authenticated call goes through `src/lib/api.ts`. On a `401` it refreshes once (de-duplicated,
  so parallel calls share a single refresh) and replays the request. The backend **rotates** the
  refresh token on every use, so the new one is always stored.
- On a failed refresh, all tokens are cleared and the user is treated as signed out.

> **Trade-off:** the roadmap ideally wants the refresh token in an `HttpOnly` cookie, which JavaScript
> cannot read. The API returns it in the response body instead, so a browser client must persist it in
> storage, where an XSS bug could read it. If that matters for the deployment, the cleanest fix is a
> small change on the API: set the refresh token as an `HttpOnly`, `Secure`, `SameSite` cookie and
> have `/auth/refresh` read it from there. Noted, not done yet.

## Scripts

| Command | Does |
|---|---|
| `npm run dev` | start the dev server (port 5173) |
| `npm run build` | type-check (`tsc -b`) + production build into `dist/` |
| `npm run preview` | serve the production build locally |
| `npm run lint` | run `oxlint` |

## Project layout

```
src/
  lib/
    api.ts            fetch wrapper: tokens, refresh-on-401, ApiError from problem+json
    auth.ts           thin functions over the /api/v1/auth endpoints
  auth/
    AuthContext.tsx   current user + login/register/logout; bootstraps from the refresh token
    ProtectedRoute.tsx route guard for signed-in-only pages
  components/
    Layout.tsx        header/nav + <Outlet/>
    Field.tsx         labelled input with inline errors
  pages/              one file per route (Home, Login, Register, VerifyEmail, Forgot, Reset, Profile)
  types.ts            TypeScript mirrors of the API DTOs
```

## Deploying

- **Vercel** (or any static host): build command `npm run build`, output dir `dist`.
- Set `VITE_API_URL` to the deployed API origin (no trailing slash).
- Add that Vercel origin to the API's `CORS_ALLOWED_ORIGINS`.
