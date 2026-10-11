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
          Pawzaar is a pet marketplace for the Philippines. Browse listings from sellers across the country,
          and sign in to post your own.
        </p>
      </div>

      {status === 'authenticated' && user ? (
        <div className="flex justify-center gap-3">
          <Button asChild size="lg">
            <Link to="/pets">Browse Pets</Link>
          </Button>
          <Button asChild size="lg" variant="secondary">
            <Link to="/pets/new">List a Pet</Link>
          </Button>
        </div>
      ) : (
        <div className="flex justify-center gap-3">
          <Button asChild size="lg">
            <Link to="/pets">Browse Pets</Link>
          </Button>
          <Button asChild size="lg" variant="secondary">
            <Link to="/register">List a Pet</Link>
          </Button>
        </div>
      )}

      <Alert className="text-left">
        <AlertDescription>
          <strong>What works today:</strong> browse all pet listings, view details, create and manage your own
          listings with photos — plus the full auth flow (register, login, verify email, reset password).
        </AlertDescription>
      </Alert>
    </div>
  )
}
