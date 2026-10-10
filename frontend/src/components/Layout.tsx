import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'

import { useAuth } from '../auth/auth-context'

export function Layout() {
  const { status, user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/')
  }

  return (
    <div className="app-shell">
      <header className="site-header">
        <div className="container header-inner">
          <Link to="/" className="brand">
            🐾 Pawzaar
          </Link>
          <nav className="nav">
            <NavLink to="/" end>
              Home
            </NavLink>
            {status === 'authenticated' && user ? (
              <>
                <NavLink to="/profile">{user.displayName}</NavLink>
                <button type="button" className="link-button" onClick={handleLogout}>
                  Log out
                </button>
              </>
            ) : status === 'loading' ? null : (
              <>
                <NavLink to="/login">Log in</NavLink>
                <NavLink to="/register" className="nav-cta">
                  Sign up
                </NavLink>
              </>
            )}
          </nav>
        </div>
      </header>

      <main className="container">
        <Outlet />
      </main>

      <footer className="site-footer">
        <div className="container">
          Pawzaar — a pet marketplace demo. API + React frontend.
        </div>
      </footer>
    </div>
  )
}
