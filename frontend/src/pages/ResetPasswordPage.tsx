import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { Field } from '../components/Field'
import { resetPassword } from '../lib/auth'
import { ApiError } from '../lib/api'

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token')

  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  if (!token) {
    return (
      <div className="auth-card">
        <h1>Reset link is missing</h1>
        <div className="alert alert-error">
          This page needs a token from your email. Open the link we emailed you, or request a new
          one.
        </div>
        <p>
          <Link className="button" to="/forgot-password">
            Request a new link
          </Link>
        </p>
      </div>
    )
  }

  // Narrowed to a plain string for use inside the submit closure below.
  const resetToken: string = token

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})

    if (newPassword !== confirm) {
      setFieldErrors({ confirm: "Passwords don't match." })
      return
    }
    if (newPassword.length < 8) {
      setFieldErrors({ newPassword: 'Password must be at least 8 characters.' })
      return
    }

    setSubmitting(true)
    try {
      await resetPassword(resetToken, newPassword)
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
        <h1>Password updated ✅</h1>
        <p>
          Your password has been changed. For your security we signed out every other session — log
          in again with your new password.
        </p>
        <p>
          <Link className="button" to="/login">
            Log in
          </Link>
        </p>
      </div>
    )
  }

  return (
    <div className="auth-card">
      <h1>Choose a new password</h1>
      <p className="muted">Pick something you haven't used before.</p>

      {error && <div className="alert alert-error">{error}</div>}

      <form onSubmit={handleSubmit} noValidate>
        <Field
          id="newPassword"
          label="New password"
          type="password"
          value={newPassword}
          onChange={setNewPassword}
          error={fieldErrors.newPassword}
          hint="8–72 characters."
          autoComplete="new-password"
          required
        />
        <Field
          id="confirm"
          label="Confirm new password"
          type="password"
          value={confirm}
          onChange={setConfirm}
          error={fieldErrors.confirm}
          autoComplete="new-password"
          required
        />
        <button className="button button-block" type="submit" disabled={submitting}>
          {submitting ? 'Saving…' : 'Set new password'}
        </button>
      </form>
    </div>
  )
}
