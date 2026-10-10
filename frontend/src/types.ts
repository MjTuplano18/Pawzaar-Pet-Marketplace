/**
 * TypeScript mirrors of the API's JSON shapes.
 *
 * These are hand-written on purpose: it keeps the frontend free of any build step that reads the
 * backend, and it makes the contract explicit. They must match the Java records in
 * `com.pawzaar.user.dto` and `com.pawzaar.user.Role`.
 */

export type Role = 'USER' | 'SELLER' | 'ADMIN'

/** Mirrors `com.pawzaar.user.dto.UserResponse`. */
export interface UserResponse {
  id: string
  email: string
  displayName: string
  phone: string | null
  role: Role
  verified: boolean
  createdAt: string
  bio: string | null
  avatarUrl: string | null
}

/** Mirrors `com.pawzaar.user.dto.TokenResponse`. */
export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
}

/** One entry of the `errors` array on a 400 validation problem. */
export interface FieldProblem {
  field: string
  message: string
}

/** Mirrors the RFC 9457 `application/problem+json` body every Pawzaar error uses. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errors?: FieldProblem[]
}
