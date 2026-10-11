import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { listPets, type PetFilters } from '@/lib/pets'
import type { PetSummary } from '@/types'

const SPECIES_ICON: Record<string, string> = { DOG: '🐶', CAT: '🐱', OTHER: '🐾' }

function formatPrice(price: string): string {
  return `₱${Number(price).toLocaleString('en-PH')}`
}

function PetCard({ pet }: { pet: PetSummary }) {
  const navigate = useNavigate()
  return (
    <Card
      className="cursor-pointer overflow-hidden transition-shadow hover:shadow-md"
      onClick={() => navigate(`/pets/${pet.id}`)}
    >
      <div className="relative aspect-[4/3] w-full overflow-hidden bg-muted">
        {pet.coverImageUrl ? (
          <img
            src={pet.coverImageUrl}
            alt={pet.title}
            className="h-full w-full object-cover"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-orange-100 to-amber-50 text-4xl">
            {SPECIES_ICON[pet.species] ?? '🐾'}
          </div>
        )}
      </div>
      <CardContent className="p-3 space-y-1">
        <p className="font-semibold truncate">{pet.title}</p>
        <div className="flex flex-wrap gap-1">
          <Badge variant="secondary">{SPECIES_ICON[pet.species]} {pet.species}</Badge>
          {pet.sex && <Badge variant="outline">{pet.sex}</Badge>}
        </div>
        <p className="text-lg font-bold text-primary">{formatPrice(pet.price)}</p>
        <p className="text-xs text-muted-foreground">{pet.city}, {pet.province}</p>
      </CardContent>
    </Card>
  )
}

function PetCardSkeleton() {
  return (
    <div className="rounded-xl border overflow-hidden">
      <Skeleton className="aspect-[4/3] w-full" />
      <div className="p-3 space-y-2">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-3 w-1/2" />
        <Skeleton className="h-5 w-1/3" />
      </div>
    </div>
  )
}

export function BrowsePage() {
  const [page, setPage] = useState(0)
  const [species, setSpecies] = useState('')
  const [city, setCity] = useState('')
  const [province, setProvince] = useState('')
  const [minPrice, setMinPrice] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [sort, setSort] = useState('createdAt')
  const [order, setOrder] = useState('desc')

  const filters: PetFilters = {
    page,
    size: 12,
    ...(species ? { species } : {}),
    ...(city ? { city } : {}),
    ...(province ? { province } : {}),
    ...(minPrice ? { minPrice: Number(minPrice) } : {}),
    ...(maxPrice ? { maxPrice: Number(maxPrice) } : {}),
    sort,
    order,
  }

  const { data, isLoading, isError } = useQuery({
    queryKey: ['pets', filters],
    queryFn: () => listPets(filters),
  })

  function handleSortChange(value: string) {
    if (value === 'price_asc') { setSort('price'); setOrder('asc') }
    else if (value === 'price_desc') { setSort('price'); setOrder('desc') }
    else if (value === 'age_asc') { setSort('ageMonths'); setOrder('asc') }
    else { setSort('createdAt'); setOrder('desc') }
    setPage(0)
  }

  const sortValue = sort === 'createdAt' ? 'newest' : sort === 'price' && order === 'asc' ? 'price_asc' : sort === 'price' ? 'price_desc' : 'age_asc'

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Browse Pets</h1>

      {/* Filter bar */}
      <div className="flex flex-wrap gap-3">
        <Select value={species || 'ALL'} onValueChange={(v) => { setSpecies(v === 'ALL' ? '' : v); setPage(0) }}>
          <SelectTrigger className="w-36">
            <SelectValue placeholder="All species" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All species</SelectItem>
            <SelectItem value="DOG">🐶 Dog</SelectItem>
            <SelectItem value="CAT">🐱 Cat</SelectItem>
            <SelectItem value="OTHER">🐾 Other</SelectItem>
          </SelectContent>
        </Select>

        <Input
          placeholder="City"
          className="w-32"
          value={city}
          onChange={(e) => { setCity(e.target.value); setPage(0) }}
        />
        <Input
          placeholder="Province"
          className="w-36"
          value={province}
          onChange={(e) => { setProvince(e.target.value); setPage(0) }}
        />
        <Input
          placeholder="Min ₱"
          type="number"
          className="w-24"
          value={minPrice}
          onChange={(e) => { setMinPrice(e.target.value); setPage(0) }}
        />
        <Input
          placeholder="Max ₱"
          type="number"
          className="w-24"
          value={maxPrice}
          onChange={(e) => { setMaxPrice(e.target.value); setPage(0) }}
        />

        <Select value={sortValue} onValueChange={handleSortChange}>
          <SelectTrigger className="w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="newest">Newest first</SelectItem>
            <SelectItem value="price_asc">Price: Low to High</SelectItem>
            <SelectItem value="price_desc">Price: High to Low</SelectItem>
            <SelectItem value="age_asc">Youngest first</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Grid */}
      {isLoading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => <PetCardSkeleton key={i} />)}
        </div>
      ) : isError ? (
        <p className="text-destructive">Failed to load listings. Please try again.</p>
      ) : data && data.content.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.content.map((pet) => <PetCard key={pet.id} pet={pet} />)}
        </div>
      ) : (
        <div className="py-16 text-center text-muted-foreground">
          <p className="text-4xl mb-3">🐾</p>
          <p className="text-lg font-medium">No pets found</p>
          <p className="text-sm">Try adjusting your filters</p>
        </div>
      )}

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</Button>
          <span className="text-sm text-muted-foreground">Page {page + 1} of {data.totalPages}</span>
          <Button variant="outline" size="sm" disabled={page >= data.totalPages - 1} onClick={() => setPage(p => p + 1)}>Next</Button>
        </div>
      )}
    </div>
  )
}
