import { Link } from 'react-router-dom'

import { useAuth } from '../auth/auth-context'

export function HomePage() {
  const { status, user } = useAuth()

  return (
    <div className="hero">
      <h1>Find your next best friend 🐾</h1>
      <p className="lede">
        Pawzaar is a pet marketplace for the Philippines. Browse listings, and sign in to post your
        own.
      </p>

      {status === 'authenticated' && user ? (
        <div className="hero-actions">
          <Link className="button" to="/profile">
            Go to your profile
          </Link>
        </div>
      ) : (
        <div className="hero-actions">
          <Link className="button" to="/register">
            Create an account
          </Link>
          <Link className="button button-secondary" to="/login">
            Log in
          </Link>
        </div>
      )}

      <div className="callout">
        <strong>What works today:</strong> register, log in, verify your email, and reset your
        password — the full authentication flow against the Pawzaar API. Listing pages come next.
      </div>
    </div>
  )
}
