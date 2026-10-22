import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { AuthContext, type AuthContextValue } from './auth-context'
import { ProtectedRoute } from './ProtectedRoute'

const anonValue: AuthContextValue = {
  user: null,
  status: 'anonymous',
  login: () => Promise.reject(new Error('not expected in test')),
  register: () => Promise.reject(new Error('not expected in test')),
  logout: () => Promise.reject(new Error('not expected in test')),
  reloadUser: () => Promise.reject(new Error('not expected in test')),
}

function renderRoutes(status: AuthContextValue['status']) {
  const value: AuthContextValue =
    status === 'authenticated'
      ? { ...anonValue, status, user: { id: 'u1', displayName: 'QA' } as AuthContextValue['user'] }
      : { ...anonValue, status }

  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route
            path="/login"
            element={
              <div>
                <p>Log in</p>
              </div>
            }
          />
          <Route element={<ProtectedRoute />}>
            <Route path="/dashboard" element={<p>Dashboard content</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  )
}

describe('ProtectedRoute', () => {
  it('renders the protected page for an authenticated user', () => {
    renderRoutes('authenticated')

    expect(screen.getByText('Dashboard content')).toBeInTheDocument()
  })

  it('redirects an anonymous user to login', () => {
    renderRoutes('anonymous')

    expect(screen.getByText('Log in')).toBeInTheDocument()
    expect(screen.queryByText('Dashboard content')).not.toBeInTheDocument()
  })

  it('shows a loading state while the auth check runs', () => {
    renderRoutes('loading')

    expect(screen.getByText('Loading…')).toBeInTheDocument()
    expect(screen.queryByText('Dashboard content')).not.toBeInTheDocument()
  })
})
