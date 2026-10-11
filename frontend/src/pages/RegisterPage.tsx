import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Field } from '../components/Field'
import { useAuth } from '../auth/auth-context'
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
      <div className="mx-auto mt-10 w-full max-w-sm">
        <Card>
          <CardHeader>
            <CardTitle className="text-2xl">Check your email 📬</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <p>
              Your account was created. We sent a verification link to <strong>{email.trim()}</strong>.
            </p>
            <p className="text-muted-foreground">
              In local development nothing is actually delivered — the link is printed in the API
              console (run the <code className="bg-muted px-1 rounded">dev</code> profile with{' '}
              <code className="bg-muted px-1 rounded">pawzaar.email.log-body=true</code>).
            </p>
          </CardContent>
          <CardFooter>
            <Button asChild className="w-full">
              <Link to="/login">Go to log in</Link>
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
          <CardTitle className="text-2xl">Create your account</CardTitle>
          <CardDescription>It takes a minute. No credit card, ever.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <form onSubmit={handleSubmit} noValidate className="space-y-2">
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
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? 'Creating account…' : 'Create account'}
            </Button>
          </form>
        </CardContent>
        <CardFooter className="text-sm text-muted-foreground">
          <span>
            Already have an account?{' '}
            <Link to="/login" className="underline underline-offset-4 hover:text-foreground">
              Log in
            </Link>
          </span>
        </CardFooter>
      </Card>
    </div>
  )
}
