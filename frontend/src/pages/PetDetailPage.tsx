import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { useAuth } from '@/auth/auth-context'
import { getPet, deletePet } from '@/lib/pets'
import {
  createAdoptionRequest,
  getRequestsForPet,
  updateAdoptionStatus,
  getMyRequests,
} from '@/lib/adoptions'
import { toast } from '@/hooks/use-toast'
import type { AdoptionRequestResponse, AdoptionStatus } from '@/types'

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

const ADOPTION_STATUS_VARIANT: Record<
  AdoptionStatus,
  'warning' | 'success' | 'destructive' | 'secondary'
> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'destructive',
  WITHDRAWN: 'secondary',
}

/** Small request card shown in the owner's adoption inbox section. */
function AdoptionRequestCard({
  req,
  onAction,
}: {
  req: AdoptionRequestResponse
  onAction: (id: string, status: AdoptionStatus) => void
}) {
  return (
    <div className="rounded-lg border p-3 space-y-2">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-medium">{req.requesterDisplayName}</p>
        <Badge variant={ADOPTION_STATUS_VARIANT[req.status]}>{req.status}</Badge>
      </div>
      {req.message && (
        <p className="text-sm text-muted-foreground line-clamp-2">{req.message}</p>
      )}
      <p className="text-xs text-muted-foreground">
        {new Date(req.createdAt).toLocaleDateString()}
      </p>
      {req.status === 'PENDING' && (
        <div className="flex gap-2">
          <Button size="sm" variant="default" onClick={() => onAction(req.id, 'APPROVED')}>
            Approve
          </Button>
          <Button
            size="sm"
            variant="destructive"
            onClick={() => onAction(req.id, 'REJECTED')}
          >
            Reject
          </Button>
        </div>
      )}
    </div>
  )
}

export function PetDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [mainImageIdx, setMainImageIdx] = useState(0)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [message, setMessage] = useState('')
  const [showRequests, setShowRequests] = useState(false)

  const { data: pet, isLoading, isError } = useQuery({
    queryKey: ['pet', id],
    queryFn: () => getPet(id!),
    enabled: !!id,
  })

  const isOwner = !!user && !!pet && user.id === pet.sellerId

  // Fetch the current user's own requests (to detect existing PENDING/APPROVED)
  const { data: myRequests } = useQuery({
    queryKey: ['myAdoptionRequests'],
    queryFn: getMyRequests,
    enabled: !!user && !isOwner,
  })

  // Fetch requests for this pet (owner only)
  const { data: petRequests } = useQuery({
    queryKey: ['petAdoptionRequests', id],
    queryFn: () => getRequestsForPet(id!),
    enabled: isOwner && showRequests,
  })

  const deleteMutation = useMutation({
    mutationFn: () => deletePet(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pets'] })
      navigate('/dashboard')
    },
    onError: () => {
      toast({
        title: 'Delete failed',
        description: 'Could not delete this listing.',
        variant: 'destructive',
      })
    },
  })

  const adoptMutation = useMutation({
    mutationFn: () => createAdoptionRequest(id!, message.trim() || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['myAdoptionRequests'] })
      setDialogOpen(false)
      setMessage('')
      toast({ title: 'Request sent!', description: 'The seller will review your request.' })
    },
    onError: (err: unknown) => {
      const detail =
        err instanceof Error ? err.message : 'Could not send your adoption request.'
      toast({ title: 'Request failed', description: detail, variant: 'destructive' })
    },
  })

  const updateStatusMutation = useMutation({
    mutationFn: ({ reqId, status }: { reqId: string; status: AdoptionStatus }) =>
      updateAdoptionStatus(reqId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['petAdoptionRequests', id] })
      toast({ title: 'Status updated' })
    },
    onError: () => {
      toast({ title: 'Update failed', variant: 'destructive' })
    },
  })

  if (isLoading) {
    return (
      <div className="grid gap-8 lg:grid-cols-2">
        <div className="space-y-3">
          <Skeleton className="aspect-[4/3] w-full rounded-xl" />
          <div className="flex gap-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-16 w-16 rounded-md" />
            ))}
          </div>
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
        <Button variant="link" onClick={() => navigate('/pets')}>
          Back to Browse
        </Button>
      </div>
    )
  }

  const mainImage = pet.images[mainImageIdx]

  // Find if the current user already has a PENDING or APPROVED request for this pet
  const existingRequest = myRequests?.find(
    (r) => r.petId === pet.id && (r.status === 'PENDING' || r.status === 'APPROVED'),
  )

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
          <Badge variant="secondary">
            {SPECIES_ICON[pet.species]} {pet.species}
          </Badge>
          {pet.breed && <Badge variant="outline">{pet.breed}</Badge>}
          {pet.sex && <Badge variant="outline">{pet.sex}</Badge>}
          <Badge variant="outline">{formatAge(pet.ageMonths)}</Badge>
        </div>

        <p className="text-muted-foreground">📍 {pet.city}, {pet.province}</p>

        {pet.description && (
          <div>
            <p className="font-medium mb-1">About</p>
            <p className="text-sm text-muted-foreground whitespace-pre-line">
              {pet.description}
            </p>
          </div>
        )}

        <div className="rounded-lg border p-4">
          <p className="text-sm font-medium text-muted-foreground">Seller</p>
          <Link
            to={`/sellers/${pet.sellerId}`}
            className="text-sm text-primary hover:underline"
          >
            View seller profile
          </Link>
        </div>

        <div className="flex flex-wrap gap-3">
          {isOwner ? (
            <>
              <Button onClick={() => navigate(`/pets/${pet.id}/edit`)}>Edit Listing</Button>
              <Button
                variant="destructive"
                onClick={handleDelete}
                disabled={deleteMutation.isPending}
              >
                {deleteMutation.isPending ? 'Deleting…' : 'Delete Listing'}
              </Button>
            </>
          ) : existingRequest ? (
            <Badge variant={ADOPTION_STATUS_VARIANT[existingRequest.status]} className="px-3 py-1.5 text-sm">
              Adoption request: {existingRequest.status}
            </Badge>
          ) : user ? (
            <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
              <DialogTrigger asChild>
                <Button>Request to Adopt</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Request to Adopt {pet.title}</DialogTitle>
                  <DialogDescription>
                    Introduce yourself to the seller. You can leave a message or just click
                    Send.
                  </DialogDescription>
                </DialogHeader>
                <Textarea
                  placeholder="Optional message to the seller…"
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  maxLength={1000}
                  rows={4}
                />
                <DialogFooter>
                  <Button
                    onClick={() => adoptMutation.mutate()}
                    disabled={adoptMutation.isPending}
                  >
                    {adoptMutation.isPending ? 'Sending…' : 'Send Request'}
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          ) : (
            <Button onClick={() => navigate('/login')}>Log in to Adopt</Button>
          )}
          <Button variant="outline" onClick={() => navigate('/pets')}>
            Back to Browse
          </Button>
        </div>

        {/* Owner: collapsible adoption requests section */}
        {isOwner && (
          <div className="space-y-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setShowRequests((v) => !v)}
            >
              {showRequests ? 'Hide' : 'Show'} Adoption Requests
            </Button>
            {showRequests && (
              <div className="space-y-2">
                {!petRequests ? (
                  <p className="text-sm text-muted-foreground">Loading…</p>
                ) : petRequests.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No adoption requests yet.</p>
                ) : (
                  petRequests.map((req) => (
                    <AdoptionRequestCard
                      key={req.id}
                      req={req}
                      onAction={(reqId, status) =>
                        updateStatusMutation.mutate({ reqId, status })
                      }
                    />
                  ))
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
