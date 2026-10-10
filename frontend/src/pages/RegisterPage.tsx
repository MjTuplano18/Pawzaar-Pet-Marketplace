import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { useAuth } from '../auth/auth-context'
import { Field } from '../components/Field'
import { ApiError } from '../lib/api'

export function RegisterPage() {
  const { register } = useAuth()

  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      await register({
        email: email.trim(),
        password,
        displayName: displayName.trim(),
        // The field is optional; omit it entirely if blank so the backend keeps it null.
        phone: phone.trim() ? phone.trim() : undefined,
      })
      setDone(true)
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.detail || err.title)
        const next: Record<string, string> = {}
        for (const fe of err.fieldErrors) next[fe.field] = fe.message
        setFieldErrors(next)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    return (
      <div className="auth-card">
        <h1>Check your email 📬</h1>
        <p>
          Your account was created. We sent a verification link to <strong>{email.trim()}</strong>.
        </p>
        <p className="muted">
          In local development nothing is actually delivered — the link is printed in the API
          console (run the <code>dev</code> profile with <code>pawzaar.email.log-body=true</code>).
        </p>
        <p>
          <Link className="button" to="/login">
            Go to log in
          </Link>
        </p>
      </div>
    )
  }

  return (
    <div className="auth-card">
      <h1>Create your account</h1>
      <p className="muted">It takes a minute. No credit card, ever.</p>

      {error && <div className="alert alert-error">{error}</div>}

      <form onSubmit={handleSubmit} noValidate>
        <Field
          id="displayName"
          label="Display name"
          value={displayName}
          onChange={setDisplayName}
          error={fieldErrors.displayName}
          autoComplete="name"
          required
        />
        <Field
          id="email"
          label="Email"
          type="email"
          value={email}
          onChange={setEmail}
          error={fieldErrors.email}
          autoComplete="email"
          required
        />
        <Field
          id="phone"
          label="Phone (optional)"
          type="tel"
          value={phone}
          onChange={setPhone}
          error={fieldErrors.phone}
          hint="Philippine mobile numbers only, e.g. +639171234567"
          autoComplete="tel"
        />
        <Field
          id="password"
          label="Password"
          type="password"
          value={password}
          onChange={setPassword}
          error={fieldErrors.password}
          hint="8–72 characters."
          autoComplete="new-password"
          required
        />

        <button className="button button-block" type="submit" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <div className="auth-links">
        <span>
          Already have an account? <Link to="/login">Log in</Link>
        </span>
      </div>
    </div>
  )
}
