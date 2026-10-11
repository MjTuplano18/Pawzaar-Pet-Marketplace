import { useState } from 'react'
import { Link } from 'react-router-dom'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '../auth/auth-context'
import { resendVerification } from '../lib/auth'
import { ApiError } from '../lib/api'

export function ProfilePage() {
  const { user, logout, reloadUser } = useAuth()
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!user) return null

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
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-semibold">Your profile</h1>

      {notice && (
        <Alert className="border-green-200 bg-green-50 text-green-800">
          <AlertDescription>{notice}</AlertDescription>
        </Alert>
      )}
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Account details</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-[160px_1fr] gap-x-4 gap-y-3 text-sm">
            <dt className="font-medium text-muted-foreground">Display name</dt>
            <dd>{user.displayName}</dd>
            <dt className="font-medium text-muted-foreground">Email</dt>
            <dd className="flex items-center gap-2">
              {user.email}
              {user.verified ? (
                <Badge variant="success">verified</Badge>
              ) : (
                <Badge variant="warning">unverified</Badge>
              )}
            </dd>
            <dt className="font-medium text-muted-foreground">Phone</dt>
            <dd>{user.phone ?? <span className="text-muted-foreground">—</span>}</dd>
            <dt className="font-medium text-muted-foreground">Role</dt>
            <dd>{user.role}</dd>
            <dt className="font-medium text-muted-foreground">Member since</dt>
            <dd>{new Date(user.createdAt).toLocaleDateString()}</dd>
          </dl>
        </CardContent>
      </Card>

      {!user.verified && (
        <Alert>
          <AlertDescription>
            Your email isn't verified yet. Check your inbox for the link, or request a new one.
          </AlertDescription>
        </Alert>
      )}

      <div className="flex flex-wrap gap-3">
        {!user.verified && (
          <Button onClick={handleResend} disabled={busy}>
            {busy ? 'Sending…' : 'Resend verification email'}
          </Button>
        )}
        <Button variant="secondary" onClick={handleRefresh}>
          Refresh
        </Button>
        <Button variant="ghost" onClick={logout}>
          Log out
        </Button>
      </div>

      <p className="text-sm text-muted-foreground">
        Listing management is coming next. For now this page proves the authenticated{' '}
        <code className="bg-muted px-1 rounded">GET /api/v1/me</code> call works end to end.
      </p>

      <p className="text-sm">
        <Link to="/" className="underline underline-offset-4 hover:text-foreground text-muted-foreground">Back home</Link>
      </p>
    </div>
  )
}
