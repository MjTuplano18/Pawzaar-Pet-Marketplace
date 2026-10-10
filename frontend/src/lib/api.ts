/**
 * The single HTTP entry point for the app.
 *
 * Responsibilities:
 *   - hold the ACCESS token in memory (it is short-lived and lost on reload by design),
 *   - keep the REFRESH token in localStorage (the backend returns it in the body, so a browser
 *     client must persist it somewhere; localStorage is the pragmatic choice here and the trade-off
 *     is documented in the README),
 *   - transparently refresh and retry ONCE on a 401, with de-duplication so ten parallel calls
 *     trigger a single refresh instead of ten,
 *   - normalise every failure into an `ApiError` built from the API's `problem+json` body.
 */

import type { ProblemDetail, TokenResponse } from '../types'

/** Empty in dev (Vite proxies /api); the deployed API origin in prod. No trailing slash. */
const API_BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/+$/, '')

const REFRESH_TOKEN_KEY = 'pawzaar.refreshToken'

let accessToken: string | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

export function clearTokens(): void {
  accessToken = null
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/** A failed response, carrying everything the UI needs from the `problem+json` body. */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly detail: string
  readonly fieldErrors: { field: string; message: string }[]

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail || problem.title || `Request failed (${status})`)
    this.name = 'ApiError'
    this.status = status
    this.title = problem.title ?? 'Request failed'
    this.detail = problem.detail ?? ''
    this.fieldErrors = problem.errors ?? []
  }

  /** The message for one form field, if the API reported a validation error for it. */
  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((e) => e.field === field)?.message
  }
}

async function parseProblem(res: Response): Promise<ProblemDetail> {
  try {
    return (await res.json()) as ProblemDetail
  } catch {
    return { title: res.statusText || 'Request failed', status: res.status }
  }
}

// De-duplicates concurrent refreshes: while one is in flight, everyone else awaits the same promise.
let refreshInFlight: Promise<boolean> | null = null

async function refreshAccessToken(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false

  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        })
        if (!res.ok) {
          clearTokens()
          return false
        }
        const tokens = (await res.json()) as TokenResponse
        setAccessToken(tokens.accessToken)
        // Rotation: the backend burns the old refresh token, so we MUST store the new one.
        setRefreshToken(tokens.refreshToken)
        return true
      } catch {
        clearTokens()
        return false
      } finally {
        refreshInFlight = null
      }
    })()
  }

  return refreshInFlight
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  /** Attach the bearer access token (and refresh-on-401). */
  auth?: boolean
}

/**
 * Runs one API call and returns the parsed JSON (or `undefined` for 204).
 * Throws `ApiError` for any non-2xx response.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = false } = options

  const send = (): Promise<Response> => {
    const headers: Record<string, string> = {}
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (auth && accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    return fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  }

  let res = await send()

  // A 401 on an authenticated call may just mean the access token expired (or was lost on reload).
  // Try one refresh (which uses the persisted refresh token), then replay the request once.
  if (res.status === 401 && auth) {
    const refreshed = await refreshAccessToken()
    if (refreshed) res = await send()
  }

  if (!res.ok) {
    throw new ApiError(res.status, await parseProblem(res))
  }

  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? (JSON.parse(text) as T) : (undefined as T))
}
