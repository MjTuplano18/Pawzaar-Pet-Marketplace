import { Link } from 'react-router-dom'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { useAuth } from '../auth/auth-context'

export function HomePage() {
  const { status, user } = useAuth()

  return (
    <div className="mx-auto max-w-2xl space-y-10 text-center">
      <div className="space-y-3">
        <h1 className="text-4xl font-bold tracking-tight">Find your next best friend 🐾</h1>
        <p className="text-lg text-muted-foreground">
          Pawzaar is a pet marketplace for the Philippines. Browse listings, and sign in to post
          your own.
        </p>
      </div>

      {status === 'authenticated' && user ? (
        <div className="flex justify-center gap-3">
          <Button asChild size="lg">
            <Link to="/profile">Go to your profile</Link>
          </Button>
        </div>
      ) : (
        <div className="flex justify-center gap-3">
          <Button asChild size="lg">
            <Link to="/register">Create an account</Link>
          </Button>
          <Button asChild size="lg" variant="secondary">
            <Link to="/login">Log in</Link>
          </Button>
        </div>
      )}

      <Alert className="text-left">
        <AlertDescription>
          <strong>What works today:</strong> register, log in, verify your email, and reset your
          password — the full authentication flow against the Pawzaar API. Listing pages come next.
        </AlertDescription>
      </Alert>
    </div>
  )
}
