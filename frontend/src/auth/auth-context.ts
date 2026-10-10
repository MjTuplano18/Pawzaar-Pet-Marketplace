import { createContext, useContext } from 'react'

import type { UserResponse } from '../types'
import type { RegisterInput } from '../lib/auth'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  user: UserResponse | null
  status: AuthStatus
  login: (email: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<UserResponse>
  logout: () => Promise<void>
  reloadUser: () => Promise<void>
}

/**
 * Kept in its own (non-component) file so the provider file exports only a component — which is
 * what React Fast Refresh needs to hot-reload cleanly.
 */
export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside an <AuthProvider>')
  return ctx
}
