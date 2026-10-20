import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { PawPrint } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { getMyPets, deletePet } from '@/lib/pets'
import { formatPrice } from '@/lib/format'
import { speciesMeta } from '@/lib/species'
import { toast } from '@/hooks/use-toast'
import type { PetSummary } from '@/types'

const STATUS_VARIANT: Record<string, 'success' | 'default' | 'secondary' | 'warning'> = {
  ACTIVE: 'success',
  SOLD: 'default',
  HIDDEN: 'secondary',
  PENDING_REVIEW: 'warning',
}

function MyPetCard({ pet, onDelete }: { pet: PetSummary; onDelete: (id: string) => void }) {
  const navigate = useNavigate()
  const { Icon: SpeciesIcon } = speciesMeta(pet.species)
  return (
    <Card className="overflow-hidden">
      <div className="relative aspect-[4/3] w-full overflow-hidden bg-muted">
        {pet.coverImageUrl ? (
          <img src={pet.coverImageUrl} alt={pet.title} className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full items-center justify-center bg-gradient-to-br from-amber-100 to-orange-50 dark:from-amber-950/40 dark:to-orange-950/25">
            <SpeciesIcon className="h-12 w-12 text-amber-500/60 dark:text-amber-400/50" strokeWidth={1.25} />
          </div>
        )}
        <div className="absolute top-2 right-2">
          <Badge variant={STATUS_VARIANT[pet.status] ?? 'secondary'}>{pet.status}</Badge>
        </div>
      </div>
      <CardContent className="p-3 space-y-2">
        <p className="font-semibold truncate">{pet.title}</p>
        <p className="text-sm font-bold text-primary">{formatPrice(pet.price)}</p>
        <p className="text-xs text-muted-foreground">{pet.city}, {pet.province}</p>
        <div className="flex gap-2">
          <Button size="sm" variant="outline" onClick={() => navigate(`/pets/${pet.id}/edit`)}>Edit</Button>
          <Button size="sm" variant="destructive" onClick={() => onDelete(pet.id)}>Delete</Button>
          <Button size="sm" variant="ghost" onClick={() => navigate(`/pets/${pet.id}`)}>View</Button>
        </div>
      </CardContent>
    </Card>
  )
}

export function DashboardPage() {
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['myPets', page],
    queryFn: () => getMyPets(page, 12),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deletePet(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['myPets'] })
      queryClient.invalidateQueries({ queryKey: ['pets'] })
      toast({ title: 'Listing deleted' })
    },
    onError: () => {
      toast({ title: 'Delete failed', variant: 'destructive' })
    },
  })

  function handleDelete(id: string) {
    if (window.confirm('Delete this listing? This cannot be undone.')) {
      deleteMutation.mutate(id)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">My Listings</h1>
        <Button asChild>
          <Link to="/pets/new">+ New Listing</Link>
        </Button>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="rounded-xl border overflow-hidden">
              <Skeleton className="aspect-[4/3] w-full" />
              <div className="p-3 space-y-2"><Skeleton className="h-4 w-3/4" /><Skeleton className="h-3 w-1/2" /></div>
            </div>
          ))}
        </div>
      ) : !data || data.content.length === 0 ? (
        <div className="py-16 text-center text-muted-foreground">
          <PawPrint className="mx-auto mb-3 h-12 w-12 text-primary/50" strokeWidth={1.25} />
          <p className="text-lg font-medium">You haven't listed any pets yet</p>
          <Button asChild className="mt-4">
            <Link to="/pets/new">List your first pet</Link>
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.content.map((pet) => (
            <MyPetCard key={pet.id} pet={pet} onDelete={handleDelete} />
          ))}
        </div>
      )}

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
