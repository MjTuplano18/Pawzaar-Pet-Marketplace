/** Thin functions over the `/api/v1/auth` endpoints. No React, no state - just the calls. */

import type { TokenResponse, UserResponse } from '../types'
import { clearTokens, getRefreshToken, request } from './api'

export interface RegisterInput {
  email: string
  password: string
  displayName: string
  phone?: string
}

export function login(email: string, password: string): Promise<TokenResponse> {
  return request<TokenResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: { email, password },
  })
}

export function register(input: RegisterInput): Promise<UserResponse> {
  return request<UserResponse>('/api/v1/auth/register', {
    method: 'POST',
    body: input,
  })
}

/** Best-effort: revoke the refresh token server-side, then drop everything locally regardless. */
export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken()
  try {
    if (refreshToken) {
      await request<void>('/api/v1/auth/logout', {
        method: 'POST',
        body: { refreshToken },
      })
    }
  } catch {
    // Ignore: the local tokens are cleared below either way, so the user is signed out.
  } finally {
    clearTokens()
  }
}

export function getMe(): Promise<UserResponse> {
  return request<UserResponse>('/api/v1/me', { auth: true })
}

export function verifyEmail(token: string): Promise<void> {
  return request<void>('/api/v1/auth/verify-email', {
    method: 'POST',
    body: { token },
  })
}

export function resendVerification(): Promise<void> {
  return request<void>('/api/v1/auth/verify-email/resend', {
    method: 'POST',
    auth: true,
  })
}

export function forgotPassword(email: string): Promise<void> {
  return request<void>('/api/v1/auth/forgot-password', {
    method: 'POST',
    body: { email },
  })
}

export function resetPassword(token: string, newPassword: string): Promise<void> {
  return request<void>('/api/v1/auth/reset-password', {
    method: 'POST',
    body: { token, newPassword },
  })
}
