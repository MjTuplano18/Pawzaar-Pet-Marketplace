import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/auth/auth-context'
import { getPet, deletePet } from '@/lib/pets'
import { toast } from '@/hooks/use-toast'

const SPECIES_ICON: Record<string, string> = { DOG: '🐶', CAT: '🐱', OTHER: '🐾' }

function formatAge(months: number): string {
  if (months < 1) return '< 1 month'
  if (months < 12) return `${months} month${months === 1 ? '' : 's'}`
  const years = Math.floor(months / 12)
  const rem = months % 12
  return rem > 0 ? `${years} yr ${rem} mo` : `${years} year${years === 1 ? '' : 's'}`
}

function formatPrice(price: string): string {
  return `₱${Number(price).toLocaleString('en-PH')}`
}

export function PetDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [mainImageIdx, setMainImageIdx] = useState(0)

  const { data: pet, isLoading, isError } = useQuery({
    queryKey: ['pet', id],
    queryFn: () => getPet(id!),
    enabled: !!id,
  })

  const deleteMutation = useMutation({
    mutationFn: () => deletePet(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pets'] })
      navigate('/dashboard')
    },
    onError: () => {
      toast({ title: 'Delete failed', description: 'Could not delete this listing.', variant: 'destructive' })
    },
  })

  if (isLoading) {
    return (
      <div className="grid gap-8 lg:grid-cols-2">
        <div className="space-y-3">
          <Skeleton className="aspect-[4/3] w-full rounded-xl" />
          <div className="flex gap-2">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-16 w-16 rounded-md" />)}</div>
        </div>
        <div className="space-y-4">
          <Skeleton className="h-8 w-2/3" />
          <Skeleton className="h-6 w-1/3" />
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-4 w-1/4" />
          <Skeleton className="h-24 w-full" />
        </div>
      </div>
    )
  }

  if (isError || !pet) {
    return (
      <div className="text-center py-16">
        <p className="text-4xl mb-3">🐾</p>
        <p className="text-lg font-medium">Listing not found</p>
        <Button variant="link" onClick={() => navigate('/pets')}>Back to Browse</Button>
      </div>
    )
  }

  const isOwner = user?.id === pet.sellerId
  const mainImage = pet.images[mainImageIdx]

  function handleDelete() {
    if (window.confirm('Delete this listing? This cannot be undone.')) {
      deleteMutation.mutate()
    }
  }

  return (
    <div className="grid gap-8 lg:grid-cols-2">
      {/* Image gallery */}
      <div className="space-y-3">
        <div className="aspect-[4/3] w-full overflow-hidden rounded-xl bg-muted">
          {mainImage ? (
            <img src={mainImage.url} alt={pet.title} className="h-full w-full object-cover" />
          ) : (
            <div className="flex h-full items-center justify-center text-6xl">
              {SPECIES_ICON[pet.species] ?? '🐾'}
            </div>
          )}
        </div>
        {pet.images.length > 1 && (
          <div className="flex gap-2">
            {pet.images.map((img, idx) => (
              <button
                key={img.id}
                onClick={() => setMainImageIdx(idx)}
                className={`h-16 w-16 overflow-hidden rounded-md border-2 transition-colors ${
                  idx === mainImageIdx ? 'border-primary' : 'border-transparent'
                }`}
              >
                <img src={img.url} alt="" className="h-full w-full object-cover" />
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Details */}
      <div className="space-y-4">
        <div>
          <h1 className="text-2xl font-bold">{pet.title}</h1>
          <p className="text-3xl font-bold text-primary mt-1">{formatPrice(pet.price)}</p>
        </div>

        <div className="flex flex-wrap gap-2">
          <Badge variant="secondary">{SPECIES_ICON[pet.species]} {pet.species}</Badge>
          {pet.breed && <Badge variant="outline">{pet.breed}</Badge>}
          {pet.sex && <Badge variant="outline">{pet.sex}</Badge>}
          <Badge variant="outline">{formatAge(pet.ageMonths)}</Badge>
        </div>

        <p className="text-muted-foreground">📍 {pet.city}, {pet.province}</p>

        {pet.description && (
          <div>
            <p className="font-medium mb-1">About</p>
            <p className="text-sm text-muted-foreground whitespace-pre-line">{pet.description}</p>
          </div>
        )}

        <div className="rounded-lg border p-4">
          <p className="text-sm font-medium text-muted-foreground">Seller</p>
          <p className="text-sm">Contact details coming soon</p>
        </div>

        <div className="flex flex-wrap gap-3">
          {isOwner ? (
            <>
              <Button onClick={() => navigate(`/pets/${pet.id}/edit`)}>Edit Listing</Button>
              <Button variant="destructive" onClick={handleDelete} disabled={deleteMutation.isPending}>
                {deleteMutation.isPending ? 'Deleting…' : 'Delete Listing'}
              </Button>
            </>
          ) : (
            <Button onClick={() => toast({ title: 'Messaging coming soon!', description: 'Contact seller feature is not available yet.' })}>
              Contact Seller
            </Button>
          )}
          <Button variant="outline" onClick={() => navigate('/pets')}>Back to Browse</Button>
        </div>
      </div>
    </div>
  )
}
