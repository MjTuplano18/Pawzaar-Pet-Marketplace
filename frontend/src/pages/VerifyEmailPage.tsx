import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { verifyEmail } from '../lib/auth'
import { ApiError } from '../lib/api'

type Status = 'idle' | 'verifying' | 'success' | 'error'

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
    <div className="mx-auto mt-10 w-full max-w-sm">
      <Card>
        <CardHeader>
          <CardTitle className="text-2xl">Email verification</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {status === 'verifying' && (
            <p className="text-sm text-muted-foreground">Verifying your email…</p>
          )}

          {status === 'success' && (
            <Alert className="border-green-200 bg-green-50 text-green-800">
              <AlertDescription>Your email is verified. You're all set! 🎉</AlertDescription>
            </Alert>
          )}

          {status === 'error' && (
            <>
              <Alert variant="destructive">
                <AlertDescription>{message}</AlertDescription>
              </Alert>
              <p className="text-sm text-muted-foreground">
                Verification links expire and work only once. Log in and request a fresh one from
                your profile.
              </p>
            </>
          )}
        </CardContent>
        <CardFooter>
          {status === 'success' && (
            <Button asChild className="w-full">
              <Link to="/login">Go to log in</Link>
            </Button>
          )}
          {status === 'error' && (
            <Button asChild variant="secondary" className="w-full">
              <Link to="/login">Back to log in</Link>
            </Button>
          )}
        </CardFooter>
      </Card>
    </div>
  )
}
