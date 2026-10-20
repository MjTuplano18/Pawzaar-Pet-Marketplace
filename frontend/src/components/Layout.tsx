import { Cat, Inbox, LayoutDashboard, LogOut, Menu, PawPrint, Plus, User, X } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { ThemeToggle } from '@/components/ThemeToggle'
import { cn } from '@/lib/utils'
import { useAuth } from '../auth/auth-context'

function initialsOf(displayName: string): string {
  const parts = displayName.trim().split(/\s+/).filter(Boolean)
  return parts
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('') || '?'
}

/** Desktop nav item with pill highlight on the active route. */
function DesktopNavItem({
  to,
  Icon,
  label,
  end = false,
}: {
  to: string
  Icon: LucideIcon
  label: string
  end?: boolean
}) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-semibold transition-colors',
          isActive
            ? 'bg-secondary text-foreground'
            : 'text-muted-foreground hover:bg-accent hover:text-foreground',
        )
      }
    >
      <Icon className="h-4 w-4" />
      {label}
    </NavLink>
  )
}

export function Layout() {
  const { status, user, logout } = useAuth()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const authenticated = status === 'authenticated' && !!user

  async function handleLogout() {
    await logout()
    setMenuOpen(false)
    navigate('/')
  }

  /** Every route in the mobile menu; tapping one closes the panel. */
  const mobileLinks: { to: string; label: string; Icon: LucideIcon; end?: boolean }[] = [
    { to: '/', label: 'Home', Icon: PawPrint, end: true },
    { to: '/pets', label: 'Browse pets', Icon: Cat },
    ...(authenticated
      ? [
          { to: '/dashboard', label: 'Dashboard', Icon: LayoutDashboard },
          { to: '/inbox', label: 'Inbox', Icon: Inbox },
          { to: '/profile', label: 'Profile', Icon: User },
        ]
      : []),
  ]

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-50 border-b bg-background/80 backdrop-blur supports-[backdrop-filter]:bg-background/70">
        <div className="mx-auto flex h-16 max-w-5xl items-center justify-between gap-3 px-4">
          <div className="flex items-center gap-2">
            <Link to="/" className="flex items-center gap-2">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
                <PawPrint className="h-5 w-5" />
              </span>
              <span className="font-display text-xl font-extrabold tracking-tight">Pawzaar</span>
            </Link>

            <nav className="hidden items-center gap-1 md:flex">
              <DesktopNavItem to="/pets" Icon={Cat} label="Browse" />
              {authenticated && (
                <>
                  <DesktopNavItem to="/dashboard" Icon={LayoutDashboard} label="Dashboard" />
                  <DesktopNavItem to="/inbox" Icon={Inbox} label="Inbox" />
                </>
              )}
            </nav>
          </div>

          <div className="flex items-center gap-1.5">
            <ThemeToggle />

            {authenticated ? (
              <>
                <Button asChild size="sm" className="hidden sm:inline-flex">
                  <Link to="/pets/new">
                    <Plus className="h-4 w-4" />
                    List a Pet
                  </Link>
                </Button>

                <Link
                  to="/profile"
                  aria-label="Your profile"
                  className="hidden h-9 w-9 items-center justify-center overflow-hidden rounded-full border bg-secondary text-xs font-bold text-secondary-foreground md:flex"
                >
                  {user.avatarUrl ? (
                    <img src={user.avatarUrl} alt="" className="h-full w-full object-cover" />
                  ) : (
                    initialsOf(user.displayName)
                  )}
                </Link>

                <Button variant="ghost" size="icon" onClick={handleLogout} aria-label="Log out">
                  <LogOut className="h-5 w-5" />
                </Button>
              </>
            ) : status === 'authenticated' ? null : (
              <div className="hidden items-center gap-1 md:flex">
                <Button asChild variant="ghost" size="sm">
                  <Link to="/login">Log in</Link>
                </Button>
                <Button asChild size="sm">
                  <Link to="/register">Sign up</Link>
                </Button>
              </div>
            )}

            <Button
              variant="ghost"
              size="icon"
              className="md:hidden"
              onClick={() => setMenuOpen((v) => !v)}
              aria-label={menuOpen ? 'Close menu' : 'Open menu'}
              aria-expanded={menuOpen}
            >
              {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </Button>
          </div>
        </div>

        {menuOpen && (
          <div className="absolute inset-x-0 top-16 z-40 border-b bg-background p-4 shadow-lg md:hidden">
            <nav className="flex flex-col gap-1">
              {mobileLinks.map(({ to, label, Icon, end }) => (
                <NavLink
                  key={to}
                  to={to}
                  end={end}
                  onClick={() => setMenuOpen(false)}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm font-semibold',
                      isActive
                        ? 'bg-secondary text-foreground'
                        : 'text-muted-foreground hover:bg-accent hover:text-foreground',
                    )
                  }
                >
                  <Icon className="h-4 w-4" />
                  {label}
                </NavLink>
              ))}

              {authenticated ? (
                <>
                  <Button asChild className="mt-2 w-full justify-center">
                    <Link to="/pets/new" onClick={() => setMenuOpen(false)}>
                      <Plus className="h-4 w-4" />
                      List a Pet
                    </Link>
                  </Button>
                  <Button variant="outline" className="w-full justify-center" onClick={handleLogout}>
                    <LogOut className="h-4 w-4" />
                    Log out
                  </Button>
                </>
              ) : (
                <>
                  <Button asChild className="mt-2 w-full justify-center">
                    <Link to="/register" onClick={() => setMenuOpen(false)}>
                      Sign up
                    </Link>
                  </Button>
                  <Button asChild variant="outline" className="w-full justify-center">
                    <Link to="/login" onClick={() => setMenuOpen(false)}>
                      Log in
                    </Link>
                  </Button>
                </>
              )}
            </nav>
          </div>
        )}
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
        <Outlet />
      </main>

      <footer className="border-t">
        <div className="mx-auto flex max-w-5xl flex-col items-center gap-3 px-4 py-6 text-center md:h-16 md:flex-row md:justify-between md:py-0 md:text-left">
          <div className="flex items-center gap-2">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary text-primary-foreground">
              <PawPrint className="h-4 w-4" />
            </span>
            <span className="font-display font-extrabold tracking-tight">Pawzaar</span>
            <span className="hidden text-xs text-muted-foreground sm:inline">
              — a pet marketplace for the Philippines
            </span>
          </div>
          <p className="text-xs text-muted-foreground">
            © 2026 Pawzaar. All rights reserved.
          </p>
        </div>
      </footer>
    </div>
  )
}
