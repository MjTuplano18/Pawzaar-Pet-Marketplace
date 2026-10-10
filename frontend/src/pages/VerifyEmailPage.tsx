import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { verifyEmail } from '../lib/auth'
import { ApiError } from '../lib/api'

type Status = 'idle' | 'verifying' | 'success' | 'error'

/**
 * Landing page for the link emailed by the API: `/verify-email?token=...`.
 *
 * The state is derived from the URL up front (a present token means we start "verifying"), and the
 * effect only runs the side effect. A ref guard makes it run exactly ONCE even under React 19's
 * StrictMode double-invoke in development — a token is single-use, so a second call would fail.
 */
export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token')

  const [status, setStatus] = useState<Status>(token ? 'verifying' : 'error')
  const [message, setMessage] = useState(token ? '' : 'This link is missing its token.')
  const attempted = useRef(false)

  useEffect(() => {
    if (attempted.current || !token) return
    attempted.current = true
    verifyEmail(token)
      .then(() => setStatus('success'))
      .catch((err) => {
        setStatus('error')
        setMessage(
          err instanceof ApiError
            ? err.detail || err.title
            : 'We could not verify this link. It may have expired.',
        )
      })
  }, [token])

  return (
    <div className="auth-card">
      <h1>Email verification</h1>

      {status === 'verifying' && <p className="muted">Verifying your email…</p>}

      {status === 'success' && (
        <>
          <div className="alert alert-success">
            Your email is verified. You're all set! 🎉
          </div>
          <p>
            <Link className="button" to="/login">
              Go to log in
            </Link>
          </p>
        </>
      )}

      {status === 'error' && (
        <>
          <div className="alert alert-error">{message}</div>
          <p className="muted">
            Verification links expire and work only once. Log in and request a fresh one from your
            profile.
          </p>
          <p>
            <Link className="button button-secondary" to="/login">
              Back to log in
            </Link>
          </p>
        </>
      )}
    </div>
  )
}
