import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useAuth } from './auth-context'

export function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <div className="flex min-h-[200px] items-center justify-center text-sm text-muted-foreground">
        Loading…
      </div>
    )
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{from: location.pathname}} />
  }

  return <Outlet />
}
