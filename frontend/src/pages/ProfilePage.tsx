import { useState } from 'react'
import { Link } from 'react-router-dom'

import { useAuth } from '../auth/auth-context'
import { resendVerification } from '../lib/auth'
import { ApiError } from '../lib/api'

export function ProfilePage() {
  const { user, logout, reloadUser } = useAuth()
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!user) return null // ProtectedRoute guarantees a user; this keeps TypeScript happy.

  async function handleResend() {
    setNotice(null)
    setError(null)
    setBusy(true)
    try {
      await resendVerification()
      setNotice('If your email still needs verifying, a fresh link is on its way.')
    } catch (err) {
      setError(err instanceof ApiError ? err.detail || err.title : 'Could not send the email.')
    } finally {
      setBusy(false)
    }
  }

  async function handleRefresh() {
    setNotice(null)
    setError(null)
    try {
      await reloadUser()
      setNotice('Profile refreshed.')
    } catch (err) {
      setError(err instanceof ApiError ? err.detail || err.title : 'Could not refresh.')
    }
  }

  return (
    <div className="profile">
      <h1>Your profile</h1>

      {notice && <div className="alert alert-success">{notice}</div>}
      {error && <div className="alert alert-error">{error}</div>}

      <dl className="profile-grid">
        <dt>Display name</dt>
        <dd>{user.displayName}</dd>
        <dt>Email</dt>
        <dd>
          {user.email}{' '}
          {user.verified ? (
            <span className="badge badge-ok">verified</span>
          ) : (
            <span className="badge badge-warn">unverified</span>
          )}
        </dd>
        <dt>Phone</dt>
        <dd>{user.phone ?? <span className="muted">—</span>}</dd>
        <dt>Role</dt>
        <dd>{user.role}</dd>
        <dt>Member since</dt>
        <dd>{new Date(user.createdAt).toLocaleDateString()}</dd>
      </dl>

      {!user.verified && (
        <div className="alert alert-info">
          Your email isn't verified yet. Check your inbox for the link, or request a new one.
        </div>
      )}

      <div className="profile-actions">
        {!user.verified && (
          <button className="button" type="button" onClick={handleResend} disabled={busy}>
            {busy ? 'Sending…' : 'Resend verification email'}
          </button>
        )}
        <button className="button button-secondary" type="button" onClick={handleRefresh}>
          Refresh
        </button>
        <button className="button button-ghost" type="button" onClick={logout}>
          Log out
        </button>
      </div>

      <p className="muted">
        Listing management is coming next. For now this page proves the authenticated
        <code> GET /api/v1/me</code> call works end to end.
      </p>

      <p>
        <Link to="/">Back home</Link>
      </p>
    </div>
  )
}
