import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
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
      <div className="mx-auto mt-10 w-full max-w-sm">
        <Card>
          <CardHeader>
            <CardTitle className="text-2xl">Reset link is missing</CardTitle>
          </CardHeader>
          <CardContent>
            <Alert variant="destructive">
              <AlertDescription>
                This page needs a token from your email. Open the link we emailed you, or request a
                new one.
              </AlertDescription>
            </Alert>
          </CardContent>
          <CardFooter>
            <Button asChild className="w-full">
              <Link to="/forgot-password">Request a new link</Link>
            </Button>
          </CardFooter>
        </Card>
      </div>
    )
  }

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
      <div className="mx-auto mt-10 w-full max-w-sm">
        <Card>
          <CardHeader>
            <CardTitle className="text-2xl">Password updated ✅</CardTitle>
          </CardHeader>
          <CardContent className="text-sm">
            <p>
              Your password has been changed. For your security we signed out every other session —
              log in again with your new password.
            </p>
          </CardContent>
          <CardFooter>
            <Button asChild className="w-full">
              <Link to="/login">Log in</Link>
            </Button>
          </CardFooter>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto mt-10 w-full max-w-sm">
      <Card>
        <CardHeader>
          <CardTitle className="text-2xl">Choose a new password</CardTitle>
          <CardDescription>Pick something you haven't used before.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <form onSubmit={handleSubmit} noValidate className="space-y-2">
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
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? 'Saving…' : 'Set new password'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
