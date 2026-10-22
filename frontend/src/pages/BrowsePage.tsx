import { MapPin, PawPrint, TriangleAlert } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useDebounced } from '@/hooks/use-debounce'
import { formatPrice, formatRelativeTime, formatSex } from '@/lib/format'
import { listPets, type PetFilters } from '@/lib/pets'
import { speciesMeta } from '@/lib/species'
import type { PetSummary } from '@/types'

/** Card for one listing: photo, chips, price, location and age of the ad. */
function PetCard({ pet }: { pet: PetSummary }) {
  const navigate = useNavigate()
  const { label, Icon } = speciesMeta(pet.species)
  const sex = formatSex(pet.sex)
  const [imageBroken, setImageBroken] = useState(false)

  const placeholder = (
    <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-amber-100 to-orange-50 dark:from-amber-950/40 dark:to-orange-950/25">
      <Icon className="h-14 w-14 text-amber-500/60 dark:text-amber-400/50" strokeWidth={1.25} />
    </div>
  )

  return (
    <Card
      className="group cursor-pointer overflow-hidden rounded-xl transition-all duration-200 hover:-translate-y-1 hover:border-primary/30 hover:shadow-lg"
      onClick={() => navigate(`/pets/${pet.id}`)}
    >
      <div className="relative aspect-[4/3] w-full overflow-hidden bg-muted">
        {pet.coverImageUrl && !imageBroken ? (
          <img
            src={pet.coverImageUrl}
            alt={pet.title}
            loading="lazy"
            onError={() => setImageBroken(true)}
            className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
          />
        ) : (
          placeholder
        )}

        {pet.status === 'SOLD' && (
          <span className="absolute left-2.5 top-2.5 rounded-full bg-foreground/85 px-2.5 py-0.5 text-[11px] font-semibold text-background">
            SOLD
          </span>
        )}
        {pet.breed && (
          <span className="absolute bottom-2.5 left-2.5 rounded-full bg-background/90 px-2 py-0.5 text-[11px] font-semibold text-foreground backdrop-blur-sm">
            {pet.breed}
          </span>
        )}
      </div>

      <CardContent className="space-y-1.5 p-3.5">
        <p className="truncate font-semibold leading-snug">{pet.title}</p>

        <div className="flex flex-wrap items-center gap-1.5">
          <Badge variant="secondary" className="gap-1 px-2 py-0.5 font-semibold">
            <Icon className="h-3 w-3" /> {label}
          </Badge>
          {sex && <Badge variant="outline" className="px-2 py-0.5">{sex}</Badge>}
        </div>

        <p className="text-lg font-extrabold text-primary">{formatPrice(pet.price)}</p>

        <div className="flex items-center justify-between gap-2 pt-0.5 text-xs text-muted-foreground">
          <span className="flex min-w-0 items-center gap-1">
            <MapPin className="h-3.5 w-3.5 shrink-0" />
            <span className="truncate">{pet.city}, {pet.province}</span>
          </span>
          <span className="shrink-0">{formatRelativeTime(pet.createdAt)}</span>
        </div>
      </CardContent>
    </Card>
  )
}

function PetCardSkeleton() {
  return (
    <div className="overflow-hidden rounded-xl border">
      <Skeleton className="aspect-[4/3] w-full rounded-none" />
      <div className="space-y-2 p-3.5">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-3 w-1/2" />
        <Skeleton className="h-5 w-1/3" />
        <Skeleton className="h-3 w-2/3" />
      </div>
    </div>
  )
}

function Grid({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">{children}</div>
}

const DEFAULT_SORT = 'createdAt'
const DEFAULT_ORDER = 'desc'

/**
 * Text filter bound to one URL parameter: the input updates instantly while
 * typing, and the URL (which drives the API query) follows after a debounce.
 *
 * Returns [value, setValue] for the input itself.
 */
function useUrlFilterInput(param: string, updateParams: UpdateParamsFn) {
  const [searchParams] = useSearchParams()
  const current = searchParams.get(param) ?? ''
  const [text, setText] = useState(current)

  useEffect(() => {
    setText(current)
  }, [current]) // history navigation or "clear filters" — follow the URL

  const debounced = useDebounced(text)
  useEffect(() => {
    // Push only once the debounce has caught up with what the user typed. A debounced
    // value older than the URL (e.g. a stale city right after "Clear filters") must not
    // overwrite the URL the user just changed.
    if (debounced !== current && debounced === text) updateParams({ [param]: debounced })
  }, [debounced, current, text, param, updateParams])

  return [text, setText] as const
}

type UpdateParamsFn = (
  patch: Record<string, string | number | null>,
  resetPage?: boolean,
) => void

const SORT_MAP: Record<string, { sort: string; order: string }> = {
  newest: { sort: 'createdAt', order: 'desc' },
  price_asc: { sort: 'price', order: 'asc' },
  price_desc: { sort: 'price', order: 'desc' },
  age_asc: { sort: 'ageMonths', order: 'asc' },
}

function FilterInput({
  value,
  onChange,
  placeholder,
  className,
  type = 'text',
}: {
  value: string
  onChange: (value: string) => void
  placeholder: string
  className: string
  type?: string
}) {
  return (
    <Input
      type={type}
      placeholder={placeholder}
      className={className}
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}

export function BrowsePage() {
  const [searchParams, setSearchParams] = useSearchParams()

  // The URL is the single source of truth for filters — a refresh or shared link restores everything.
  const species = searchParams.get('species') ?? ''
  const cityParam = searchParams.get('city') ?? ''
  const provinceParam = searchParams.get('province') ?? ''
  const minPriceParam = searchParams.get('minPrice') ?? ''
  const maxPriceParam = searchParams.get('maxPrice') ?? ''
  const sort = searchParams.get('sort') ?? DEFAULT_SORT
  const order = searchParams.get('order') ?? DEFAULT_ORDER
  const page = Math.max(0, Number(searchParams.get('page') ?? '0') || 0)

  /** Merge a patch into the query string; drops empty values and (by default) resets to page 1. */
  const updateParams = useCallback<UpdateParamsFn>(
    (patch, resetPage = true) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          for (const [key, value] of Object.entries(patch)) {
            if (value === null || value === '') next.delete(key)
            else next.set(key, String(value))
          }
          if (resetPage) next.delete('page')
          return next
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  const [cityText, setCityText] = useUrlFilterInput('city', updateParams)
  const [provinceText, setProvinceText] = useUrlFilterInput('province', updateParams)
  const [minPriceText, setMinPriceText] = useUrlFilterInput('minPrice', updateParams)
  const [maxPriceText, setMaxPriceText] = useUrlFilterInput('maxPrice', updateParams)

  const filters: PetFilters = {
    page,
    size: 12,
    ...(species ? { species } : {}),
    ...(cityParam ? { city: cityParam } : {}),
    ...(provinceParam ? { province: provinceParam } : {}),
    ...(minPriceParam ? { minPrice: Number(minPriceParam) } : {}),
    ...(maxPriceParam ? { maxPrice: Number(maxPriceParam) } : {}),
    sort,
    order,
  }

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['pets', filters],
    queryFn: () => listPets(filters),
  })

  const hasActiveFilters =
    !!species || !!cityParam || !!provinceParam || !!minPriceParam || !!maxPriceParam

  function clearFilters() {
    setCityText('')
    setProvinceText('')
    setMinPriceText('')
    setMaxPriceText('')
    setSearchParams({}, { replace: true })
  }

  const sortValue =
    sort === 'createdAt'
      ? 'newest'
      : sort === 'price' && order === 'asc'
        ? 'price_asc'
        : sort === 'price'
          ? 'price_desc'
          : 'age_asc'

  const totalResults = data?.totalElements

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h1 className="text-2xl font-extrabold tracking-tight">Browse pets</h1>
        {totalResults != null && (
          <p className="text-sm text-muted-foreground">
            {totalResults} {totalResults === 1 ? 'listing' : 'listings'}
          </p>
        )}
      </div>

      {/* Filter bar */}
      <div className="rounded-xl border bg-card p-4">
        <div className="mb-3 flex items-center justify-between">
          <p className="text-sm font-semibold">Filters</p>
          {hasActiveFilters && (
            <Button variant="ghost" size="sm" className="h-7 px-2" onClick={clearFilters}>
              Clear filters
            </Button>
          )}
        </div>
        <div className="flex flex-wrap gap-2.5">
          <Select
            value={species || 'ALL'}
            onValueChange={(v) => updateParams({ species: v === 'ALL' ? '' : v })}
          >
            <SelectTrigger className="w-40">
              <SelectValue placeholder="All species" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All species</SelectItem>
              <SelectItem value="DOG">Dogs</SelectItem>
              <SelectItem value="CAT">Cats</SelectItem>
              <SelectItem value="OTHER">Other pets</SelectItem>
            </SelectContent>
          </Select>

          <FilterInput value={cityText} onChange={setCityText} placeholder="City" className="w-32" />
          <FilterInput value={provinceText} onChange={setProvinceText} placeholder="Province" className="w-36" />
          <FilterInput value={minPriceText} onChange={setMinPriceText} placeholder="Min ₱" type="number" className="w-24" />
          <FilterInput value={maxPriceText} onChange={setMaxPriceText} placeholder="Max ₱" type="number" className="w-24" />

          <Select value={sortValue} onValueChange={(v) => updateParams(SORT_MAP[v] ?? SORT_MAP.newest)}>
            <SelectTrigger className="w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="newest">Newest first</SelectItem>
              <SelectItem value="price_asc">Price: low to high</SelectItem>
              <SelectItem value="price_desc">Price: high to low</SelectItem>
              <SelectItem value="age_asc">Youngest first</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* Grid */}
      {isLoading ? (
        <Grid>{Array.from({ length: 8 }).map((_, i) => <PetCardSkeleton key={i} />)}</Grid>
      ) : isError ? (
        <div className="py-16 text-center">
          <TriangleAlert className="mx-auto mb-3 h-10 w-10 text-destructive/70" />
          <p className="text-lg font-semibold">Couldn't load listings</p>
          <p className="mb-4 text-sm text-muted-foreground">
            Something went wrong while loading listings. Check your connection, then try again.
          </p>
          <Button variant="outline" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : data && data.content.length > 0 ? (
        <Grid>{data.content.map((pet) => <PetCard key={pet.id} pet={pet} />)}</Grid>
      ) : (
        <div className="py-16 text-center">
          <span className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-secondary">
            <PawPrint className="h-8 w-8 text-primary/70" />
          </span>
          <p className="text-lg font-semibold">No pets found</p>
          <p className="text-sm text-muted-foreground">Nothing matches these filters yet.</p>
          {hasActiveFilters && (
            <Button variant="outline" size="sm" className="mt-4" onClick={clearFilters}>
              Clear filters
            </Button>
          )}
        </div>
      )}

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 pt-2">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => updateParams({ page: page - 1 }, false)}
          >
            Previous
          </Button>
          <span className="text-sm text-muted-foreground">
            Page {page + 1} of {data.totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= data.totalPages - 1}
            onClick={() => updateParams({ page: page + 1 }, false)}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  )
}
