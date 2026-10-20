import { ArrowRight, HeartHandshake, Image, Search, Sparkles } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
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

  // Real number of live listings from the API — keeps the hero honest as data grows.
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
          <Badge variant="secondary" className="gap-1.5 px-3 py-1 text-xs">
            <Sparkles className="h-3.5 w-3.5 text-primary" />
            {listingCount != null
              ? `${listingCount} ${listingCount === 1 ? 'pet is' : 'pets are'} looking for a home`
              : 'Pets are looking for a home'}
          </Badge>

          <h1 className="text-4xl font-extrabold leading-tight tracking-tight md:text-5xl">
            Find your next best friend in the Philippines
          </h1>
          <p className="text-lg leading-relaxed text-muted-foreground">
            Pawzaar is a pet marketplace where sellers post real listings with photos, and adopters
            reach out directly. Every listing you see is served live from the Pawzaar API.
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
            Three steps from browsing to a new best friend.
          </p>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          <FeatureCard
            Icon={Search}
            title="Browse real listings"
            description="Filter by species, price and location. Every card links to a full pet page with photos and details."
          />
          <FeatureCard
            Icon={Image}
            title="Sell with photos"
            description="List a pet in minutes — upload multiple photos, set location and price, and manage it from your dashboard."
          />
          <FeatureCard
            Icon={HeartHandshake}
            title="Adopt through requests"
            description="Found your match? Send the seller an adoption request and track its status in your inbox."
          />
        </div>
      </section>
    </div>
  )
}
