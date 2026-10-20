import { ArrowRight, HeartHandshake, Image, Search } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { listPets } from '@/lib/pets'
import { useAuth } from '../auth/auth-context'

/** Tiny card describing one thing Pawzaar actually does today. */
function FeatureCard({
  Icon,
  title,
  description,
}: {
  Icon: LucideIcon
  title: string
  description: string
}) {
  return (
    <Card className="rounded-xl transition-shadow hover:shadow-md">
      <CardContent className="p-5 space-y-2">
        <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <Icon className="h-5 w-5" />
        </span>
        <p className="font-bold">{title}</p>
        <p className="text-sm leading-relaxed text-muted-foreground">{description}</p>
      </CardContent>
    </Card>
  )
}

export function HomePage() {
  const { status } = useAuth()

  // Real number of active listings from the API — keeps the hero factual as data grows.
  const { data } = useQuery({
    queryKey: ['pets', 'home-count'],
    queryFn: () => listPets({ page: 0, size: 1 }),
    staleTime: 60_000,
  })
  const listingCount = data?.totalElements

  return (
    <div className="space-y-12">
      {/* Hero */}
      <section className="relative overflow-hidden rounded-3xl border bg-gradient-to-br from-amber-100/80 via-orange-50/60 to-rose-100/50 px-6 py-14 text-center dark:from-amber-950/40 dark:via-orange-950/25 dark:to-rose-950/20">
        <div className="mx-auto max-w-2xl space-y-6">
          {listingCount != null && (
            <p className="inline-flex items-center gap-2 rounded-full border bg-background/80 px-3 py-1 text-xs font-semibold text-muted-foreground">
              <span className="relative flex h-2 w-2">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
                <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
              </span>
              {listingCount} active {listingCount === 1 ? 'listing' : 'listings'}
            </p>
          )}

          <h1 className="text-4xl font-extrabold leading-tight tracking-tight md:text-5xl">
            Buy and sell pets across the Philippines
          </h1>
          <p className="text-lg leading-relaxed text-muted-foreground">
            Browse listings from sellers nationwide, filter by species, price, and location, and
            send an adoption request when you find the right match. Listing a pet takes minutes:
            add photos, set a price, and publish.
          </p>

          <div className="flex flex-wrap items-center justify-center gap-3">
            <Button asChild size="lg" className="rounded-xl">
              <Link to="/pets">
                Browse pets
                <ArrowRight className="h-4 w-4" />
              </Link>
            </Button>
            {status === 'authenticated' ? (
              <Button asChild variant="secondary" size="lg" className="rounded-xl">
                <Link to="/pets/new">List a pet</Link>
              </Button>
            ) : (
              <Button asChild variant="secondary" size="lg" className="rounded-xl">
                <Link to="/register">Create an account</Link>
              </Button>
            )}
          </div>
        </div>
      </section>

      {/* What you can do */}
      <section className="space-y-4">
        <div className="text-center">
          <h2 className="text-2xl font-extrabold tracking-tight">How Pawzaar works</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            From browsing to bringing your pet home, in three steps.
          </p>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          <FeatureCard
            Icon={Search}
            title="Browse listings"
            description="Filter by species, breed, price, and location to see what's available near you."
          />
          <FeatureCard
            Icon={Image}
            title="Sell your pet"
            description="Create a listing with photos, set your price, and manage it from your dashboard."
          />
          <FeatureCard
            Icon={HeartHandshake}
            title="Send an adoption request"
            description="Message the seller with your details, and track the request's status as it's reviewed."
          />
        </div>
      </section>
    </div>
  )
}
