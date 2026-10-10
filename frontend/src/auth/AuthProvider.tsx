import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import type { UserResponse } from '../types'
import { clearTokens, getRefreshToken, setAccessToken, setRefreshToken } from '../lib/api'
import * as authApi from '../lib/auth'
import type { RegisterInput } from '../lib/auth'
import { AuthContext } from './auth-context'
import type { AuthContextValue, AuthStatus } from './auth-context'

/**
 * Holds the signed-in user for the whole app.
 *
 * On first mount it "bootstraps": if a refresh token is persisted, it asks the API who the user is
 * (`GET /me`). The api layer automatically refreshes the access token first, so the user stays
 * signed in across a page reload even though the access token lives only in memory.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')

  useEffect(() => {
    let active = true

    async function bootstrap() {
      if (!getRefreshToken()) {
        setStatus('anonymous')
        return
      }
      try {
        const me = await authApi.getMe()
        if (!active) return
        setUser(me)
        setStatus('authenticated')
      } catch {
        if (!active) return
        clearTokens()
        setUser(null)
        setStatus('anonymous')
      }
    }

    void bootstrap()
    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await authApi.login(email, password)
    setAccessToken(tokens.accessToken)
    setRefreshToken(tokens.refreshToken)
    const me = await authApi.getMe()
    setUser(me)
    setStatus('authenticated')
  }, [])

  const register = useCallback((input: RegisterInput) => authApi.register(input), [])

  const logout = useCallback(async () => {
    await authApi.logout()
    setUser(null)
    setStatus('anonymous')
  }, [])

  const reloadUser = useCallback(async () => {
    const me = await authApi.getMe()
    setUser(me)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, status, login, register, logout, reloadUser }),
    [user, status, login, register, logout, reloadUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
