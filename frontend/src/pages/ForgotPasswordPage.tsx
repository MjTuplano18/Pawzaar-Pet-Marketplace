import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
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
    return (
      <div className="mx-auto mt-10 w-full max-w-sm">
        <Card>
          <CardHeader>
            <CardTitle className="text-2xl">Check your email 📬</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <p>
              If an account exists for <strong>{email.trim()}</strong>, we've sent a link to reset
              your password.
            </p>
            <p className="text-muted-foreground">
              The link expires in 1 hour. In local development it is printed in the API console
              instead of being delivered.
            </p>
          </CardContent>
          <CardFooter>
            <Button asChild className="w-full">
              <Link to="/login">Back to log in</Link>
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
          <CardTitle className="text-2xl">Forgot your password?</CardTitle>
          <CardDescription>Enter your email and we'll send you a reset link.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <form onSubmit={handleSubmit} noValidate className="space-y-2">
            <Field
              id="email"
              label="Email"
              type="email"
              value={email}
              onChange={setEmail}
              autoComplete="email"
              required
            />
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? 'Sending…' : 'Send reset link'}
            </Button>
          </form>
        </CardContent>
        <CardFooter className="text-sm text-muted-foreground">
          <span>
            Remembered it?{' '}
            <Link to="/login" className="underline underline-offset-4 hover:text-foreground">
              Back to log in
            </Link>
          </span>
        </CardFooter>
      </Card>
    </div>
  )
}
