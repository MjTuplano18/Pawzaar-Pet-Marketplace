import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="auth-card">
      <h1>Page not found</h1>
      <p className="muted">The page you're looking for doesn't exist.</p>
      <p>
        <Link className="button" to="/">
          Back home
        </Link>
      </p>
    </div>
  )
}
