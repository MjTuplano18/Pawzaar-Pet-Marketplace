import { Heart, MapPin, NotebookPen, PawPrint, Pencil, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { ConfirmDialog } from '@/components/ConfirmDialog'
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
import { formatAge, formatPrice, formatRelativeTime, formatSex } from '@/lib/format'
import { speciesMeta } from '@/lib/species'
import { getPet, deletePet } from '@/lib/pets'
import {
  createAdoptionRequest,
  getRequestsForPet,
  updateAdoptionStatus,
  getMyRequests,
} from '@/lib/adoptions'
import { notify } from '@/lib/notify'
import type { AdoptionRequestResponse, AdoptionStatus, PetImageResponse } from '@/types'

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
    <div className="rounded-xl border p-3.5 space-y-2 transition-colors hover:bg-accent/40">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-semibold">{req.requesterDisplayName}</p>
        <Badge variant={ADOPTION_STATUS_VARIANT[req.status]}>{req.status}</Badge>
      </div>
      {req.message && (
        <p className="text-sm leading-relaxed text-muted-foreground line-clamp-2">{req.message}</p>
      )}
      <p className="text-xs text-muted-foreground">
        {formatRelativeTime(req.createdAt)}
      </p>
      {req.status === 'PENDING' && (
        <div className="flex gap-2">
          <Button size="sm" onClick={() => onAction(req.id, 'APPROVED')}>
            Approve
          </Button>
          <Button size="sm" variant="destructive" onClick={() => onAction(req.id, 'REJECTED')}>
            Reject
          </Button>
        </div>
      )}
    </div>
  )
}

/** Photo gallery with thumbnail strip; falls back to the species icon if a photo fails to load. */
function Gallery({
  images,
  title,
  species,
}: {
  images: PetImageResponse[]
  title: string
  species: string
}) {
  const [mainIdx, setMainIdx] = useState(0)
  const [mainBroken, setMainBroken] = useState(false)
  const { Icon } = speciesMeta(species)
  const main = images[Math.min(mainIdx, Math.max(0, images.length - 1))]

  const placeholder = (
    <div className="flex h-full items-center justify-center bg-gradient-to-br from-amber-100 to-orange-50 dark:from-amber-950/40 dark:to-orange-950/25">
      <Icon className="h-24 w-24 text-amber-500/50 dark:text-amber-400/40" strokeWidth={1} />
    </div>
  )

  return (
    <div className="space-y-3">
      <div className="aspect-[4/3] w-full overflow-hidden rounded-2xl border bg-muted">
        {main && !mainBroken ? (
          <img
            key={main.id}
            src={main.url}
            alt={title}
            onError={() => setMainBroken(true)}
            className="h-full w-full object-cover"
          />
        ) : (
          placeholder
        )}
      </div>
      {images.length > 1 && (
        <div className="flex gap-2 overflow-x-auto pb-1">
          {images.map((img, idx) => (
            <button
              key={img.id}
              onClick={() => setMainIdx(idx)}
              aria-label={`Show photo ${idx + 1}`}
              className={`h-16 w-16 shrink-0 overflow-hidden rounded-lg border-2 transition-all ${
                idx === mainIdx
                  ? 'border-primary shadow-sm'
                  : 'border-transparent opacity-70 hover:opacity-100'
              }`}
            >
              <img src={img.url} alt="" className="h-full w-full object-cover" />
            </button>
          ))}
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
  const [adoptDialogOpen, setAdoptDialogOpen] = useState(false)
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)
  const [message, setMessage] = useState('')
  const [showRequests, setShowRequests] = useState(false)

  const { data: pet, isLoading, isError } = useQuery({
    queryKey: ['pet', id],
    queryFn: () => getPet(id!),
    enabled: !!id,
  })

  const isOwner = !!user && !!pet && user.id === pet.sellerId

  // Fetch the current user's own requests (to detect an existing PENDING/APPROVED one)
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
      notify.success('Listing deleted')
      navigate('/dashboard')
    },
    onError: () => {
      setConfirmDeleteOpen(false)
      notify.error('Delete failed', 'Could not delete this listing.')
    },
  })

  const adoptMutation = useMutation({
    mutationFn: () => createAdoptionRequest(id!, message.trim() || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['myAdoptionRequests'] })
      setAdoptDialogOpen(false)
      setMessage('')
      notify.success('Request sent!', 'The seller will review your request.')
    },
    onError: (err: unknown) => {
      const detail = err instanceof Error ? err.message : 'Could not send your adoption request.'
      notify.error('Request failed', detail)
    },
  })

  const updateStatusMutation = useMutation({
    mutationFn: ({ reqId, status }: { reqId: string; status: AdoptionStatus }) =>
      updateAdoptionStatus(reqId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['petAdoptionRequests', id] })
      notify.success('Status updated')
    },
    onError: () => {
      notify.error('Status update failed')
    },
  })

  if (!id || isError || !pet) {
    return (
      <div className="py-16 text-center">
        <PawPrint className="mx-auto mb-4 h-14 w-14 text-primary/40" strokeWidth={1.25} />
        <p className="text-lg font-semibold">Listing not found</p>
        <p className="mb-4 text-sm text-muted-foreground">
          This listing doesn't exist or was removed.
        </p>
        <Button asChild variant="outline">
          <Link to="/pets">Back to browse</Link>
        </Button>
      </div>
    )
  }

  if (isLoading || !pet) {
    return (
      <div className="mx-auto max-w-6xl">
        <div className="grid gap-8 lg:grid-cols-2">
        <div className="space-y-3">
          <Skeleton className="aspect-[4/3] w-full rounded-2xl" />
          <div className="flex gap-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-16 w-16 rounded-lg" />
            ))}
          </div>
        </div>
        <div className="space-y-4">
          <Skeleton className="h-8 w-2/3" />
          <Skeleton className="h-10 w-1/3" />
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-4 w-1/4" />
          <Skeleton className="h-24 w-full" />
        </div>
      </div>
    </div>
  )
}

  const { Icon: SpeciesIcon } = speciesMeta(pet.species)

  // Find if the current user already has a PENDING or APPROVED request for this pet
  const existingRequest = myRequests?.find(
    (r) => r.petId === pet.id && (r.status === 'PENDING' || r.status === 'APPROVED'),
  )

  return (
    <div className="mx-auto max-w-6xl space-y-4">
      <Link
        to="/pets"
        className="inline-flex items-center gap-1.5 text-sm font-semibold text-muted-foreground hover:text-foreground"
      >
        ← Back to browse
      </Link>

      <div className="grid gap-8 lg:grid-cols-2">
        <Gallery key={pet.id} images={pet.images} title={pet.title} species={pet.species} />

        {/* Details */}
        <div className="space-y-5">
          <div className="space-y-1.5">
            <h1 className="text-3xl font-extrabold leading-tight tracking-tight">{pet.title}</h1>
            <p className="text-3xl font-extrabold text-primary">{formatPrice(pet.price)}</p>
          </div>

          <div className="flex flex-wrap gap-2">
            <Badge variant="secondary" className="gap-1 px-2.5 py-1 font-semibold">
              <SpeciesIcon className="h-3.5 w-3.5" /> {speciesMeta(pet.species).label}
            </Badge>
            {pet.breed && <Badge variant="outline">{pet.breed}</Badge>}
            {formatSex(pet.sex) && <Badge variant="outline">{formatSex(pet.sex)}</Badge>}
            <Badge variant="outline">{formatAge(pet.ageMonths)}</Badge>
          </div>

          <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
            <MapPin className="h-4 w-4 text-primary" />
            {pet.city}, {pet.province}
          </p>

          {pet.description && (
            <div className="rounded-xl border bg-card p-4">
              <p className="mb-1.5 text-sm font-semibold">About</p>
              <p className="whitespace-pre-line text-sm leading-relaxed text-muted-foreground">
                {pet.description}
              </p>
            </div>
          )}

          <div className="flex items-center gap-3 rounded-xl border bg-card p-4">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-secondary">
              <SpeciesIcon className="h-5 w-5 text-primary" />
            </span>
            <div className="min-w-0">
              <p className="text-sm font-semibold">Listed on Pawzaar</p>
              <p className="text-xs text-muted-foreground">
                Posted {formatRelativeTime(pet.createdAt)} · Updated {formatRelativeTime(pet.updatedAt)}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap gap-2.5">
            {isOwner ? (
              <>
                <Button onClick={() => navigate(`/pets/${pet.id}/edit`)}>
                  <Pencil className="h-4 w-4" />
                  Edit listing
                </Button>
                <Button variant="destructive" onClick={() => setConfirmDeleteOpen(true)}>
                  <Trash2 className="h-4 w-4" />
                  Delete listing
                </Button>
              </>
            ) : existingRequest ? (
              <Badge
                variant={ADOPTION_STATUS_VARIANT[existingRequest.status]}
                className="px-3 py-1.5 text-sm"
              >
                Your adoption request: {existingRequest.status.toLowerCase()}
              </Badge>
            ) : user ? (
              <Dialog open={adoptDialogOpen} onOpenChange={setAdoptDialogOpen}>
                <DialogTrigger asChild>
                  <Button>
                    <Heart className="h-4 w-4" />
                    Request to adopt
                  </Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>Request to adopt {pet.title}</DialogTitle>
                    <DialogDescription>
                      Introduce yourself to the seller — you can leave a message or just send the
                      request.
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
                    <Button onClick={() => adoptMutation.mutate()} disabled={adoptMutation.isPending}>
                      {adoptMutation.isPending
                        ? 'Sending…'
                        : (
                          <>
                            <NotebookPen className="h-4 w-4" /> Send request
                          </>
                        )}
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            ) : (
              <Button onClick={() => navigate('/login')}>
                <Heart className="h-4 w-4" />
                Log in to adopt
              </Button>
            )}
          </div>

          {/* Owner: collapsible adoption requests section */}
          {isOwner && (
            <div className="space-y-2">
              <Button variant="outline" size="sm" onClick={() => setShowRequests((v) => !v)}>
                {showRequests ? 'Hide' : 'Show'} adoption requests
              </Button>
              {showRequests && (
                <div className="space-y-2">
                  {!petRequests ? (
                    <p className="text-sm text-muted-foreground">Loading requests…</p>
                  ) : petRequests.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No adoption requests yet.</p>
                  ) : (
                    petRequests.map((req) => (
                      <AdoptionRequestCard
                        key={req.id}
                        req={req}
                        onAction={(reqId, status) => updateStatusMutation.mutate({ reqId, status })}
                      />
                    ))
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={confirmDeleteOpen}
        onOpenChange={setConfirmDeleteOpen}
        title="Delete this listing?"
        description="The listing and its photos will be permanently removed. This cannot be undone."
        confirmLabel="Delete listing"
        destructive
        pending={deleteMutation.isPending}
        onConfirm={() => deleteMutation.mutate()}
      />
    </div>
  )
}
