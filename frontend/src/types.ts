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

export type Species = 'DOG' | 'CAT' | 'OTHER'
export type PetStatus = 'ACTIVE' | 'SOLD' | 'HIDDEN' | 'PENDING_REVIEW'

export interface PetImageResponse {
  id: string
  url: string
  contentType: string
  sortOrder: number
}

export interface PetSummary {
  id: string
  title: string
  species: Species
  breed: string | null
  price: string
  city: string
  province: string
  sex: string | null
  status: PetStatus
  createdAt: string
  coverImageUrl: string | null
}

export interface PetResponse {
  id: string
  sellerId: string
  title: string
  species: Species
  breed: string | null
  ageMonths: number
  price: string
  description: string | null
  city: string
  province: string
  sex: string | null
  status: PetStatus
  createdAt: string
  updatedAt: string
  images: PetImageResponse[]
}

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
