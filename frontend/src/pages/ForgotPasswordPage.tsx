import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { Field } from '../components/Field'
import { forgotPassword } from '../lib/auth'
import { ApiError } from '../lib/api'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await forgotPassword(email.trim())
      setDone(true)
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.detail || err.title
          : 'Something went wrong. Please try again.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    // Deliberately identical whether or not the address exists: the API never reveals it, and
    // neither should we.
    return (
      <div className="auth-card">
        <h1>Check your email 📬</h1>
        <p>
          If an account exists for <strong>{email.trim()}</strong>, we've sent a link to reset your
          password.
        </p>
        <p className="muted">
          The link expires in 1 hour. In local development it is printed in the API console instead
          of being delivered.
        </p>
        <p>
          <Link className="button" to="/login">
            Back to log in
          </Link>
        </p>
      </div>
    )
  }

  return (
    <div className="auth-card">
      <h1>Forgot your password?</h1>
      <p className="muted">Enter your email and we'll send you a reset link.</p>

      {error && <div className="alert alert-error">{error}</div>}

      <form onSubmit={handleSubmit} noValidate>
        <Field
          id="email"
          label="Email"
          type="email"
          value={email}
          onChange={setEmail}
          autoComplete="email"
          required
        />
        <button className="button button-block" type="submit" disabled={submitting}>
          {submitting ? 'Sending…' : 'Send reset link'}
        </button>
      </form>

      <div className="auth-links">
        <span>
          Remembered it? <Link to="/login">Back to log in</Link>
        </span>
      </div>
    </div>
  )
}
