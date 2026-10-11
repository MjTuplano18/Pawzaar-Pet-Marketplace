import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { useAuth } from '../auth/auth-context'

export function Layout() {
  const { status, user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/')
  }

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-50 border-b bg-background">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <Link to="/" className="text-lg font-bold tracking-tight hover:opacity-80">
            🐾 Pawzaar
          </Link>
          <nav className="flex items-center gap-1">
            <NavLink
              to="/"
              end
              className={({ isActive }) =>
                isActive
                  ? 'text-sm font-medium px-3 py-1.5 text-foreground'
                  : 'text-sm font-medium px-3 py-1.5 text-muted-foreground hover:text-foreground'
              }
            >
              Home
            </NavLink>
            {status === 'authenticated' && user ? (
              <>
                <NavLink
                  to="/profile"
                  className={({ isActive }) =>
                    isActive
                      ? 'text-sm font-medium px-3 py-1.5 text-foreground'
                      : 'text-sm font-medium px-3 py-1.5 text-muted-foreground hover:text-foreground'
                  }
                >
                  {user.displayName}
                </NavLink>
                <Button variant="ghost" size="sm" onClick={handleLogout}>
                  Log out
                </Button>
              </>
            ) : status === 'loading' ? null : (
              <>
                <NavLink
                  to="/login"
                  className={({ isActive }) =>
                    isActive
                      ? 'text-sm font-medium px-3 py-1.5 text-foreground'
                      : 'text-sm font-medium px-3 py-1.5 text-muted-foreground hover:text-foreground'
                  }
                >
                  Log in
                </NavLink>
                <Button asChild size="sm">
                  <Link to="/register">Sign up</Link>
                </Button>
              </>
            )}
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-10">
        <Outlet />
      </main>

      <Separator />
      <footer className="py-4 text-center text-xs text-muted-foreground">
        Pawzaar — a pet marketplace demo. API + React frontend.
      </footer>
    </div>
  )
}
