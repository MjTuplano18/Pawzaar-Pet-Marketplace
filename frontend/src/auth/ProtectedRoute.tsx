import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useAuth } from './auth-context'

/**
 * Guards a group of routes. While the auth state is still being restored we render a placeholder
 * (so we never flash the login page for an already-signed-in user); anonymous visitors are sent to
 * /login with the page they wanted, so login can send them back there afterwards.
 */
export function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <div className="page-loading">Loading…</div>
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <Outlet />
}
