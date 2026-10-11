import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { getMyRequests, getReceivedRequests, updateAdoptionStatus } from '@/lib/adoptions'
import { toast } from '@/hooks/use-toast'
import type { AdoptionRequestResponse, AdoptionStatus } from '@/types'

/** Badge variant per adoption status. */
function statusVariant(
  status: AdoptionStatus,
): 'warning' | 'success' | 'destructive' | 'secondary' {
  switch (status) {
    case 'PENDING':
      return 'warning'
    case 'APPROVED':
      return 'success'
    case 'REJECTED':
      return 'destructive'
    case 'WITHDRAWN':
      return 'secondary'
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-PH', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

interface RequestCardProps {
  req: AdoptionRequestResponse
  /** 'sent' = current user is the requester; 'received' = current user is the pet owner */
  role: 'sent' | 'received'
  onAction: (id: string, status: AdoptionStatus) => void
  isPending: boolean
}

function RequestCard({ req, role, onAction, isPending }: RequestCardProps) {
  return (
    <div className="rounded-lg border p-4 space-y-2">
      <div className="flex items-start justify-between gap-2">
        <div className="space-y-0.5">
          <Link
            to={`/inbox/${req.id}`}
            className="font-semibold hover:underline text-sm"
          >
            {req.petTitle}
          </Link>
          <p className="text-xs text-muted-foreground">
            {role === 'sent'
              ? `Sent on ${formatDate(req.createdAt)}`
              : `From ${req.requesterDisplayName} · ${formatDate(req.createdAt)}`}
          </p>
        </div>
        <Badge variant={statusVariant(req.status)}>{req.status}</Badge>
      </div>

      {req.message && (
        <p className="text-sm text-muted-foreground line-clamp-2">{req.message}</p>
      )}

      <div className="flex gap-2 pt-1">
        {role === 'sent' && req.status === 'PENDING' && (
          <Button
            size="sm"
            variant="outline"
            disabled={isPending}
            onClick={() => onAction(req.id, 'WITHDRAWN')}
          >
            Withdraw
          </Button>
        )}
        {role === 'received' && req.status === 'PENDING' && (
          <>
            <Button
              size="sm"
              variant="default"
              disabled={isPending}
              onClick={() => onAction(req.id, 'APPROVED')}
            >
              Approve
            </Button>
            <Button
              size="sm"
              variant="destructive"
              disabled={isPending}
              onClick={() => onAction(req.id, 'REJECTED')}
            >
              Reject
            </Button>
          </>
        )}
      </div>
    </div>
  )
}

function SkeletonCards() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="rounded-lg border p-4 space-y-2">
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-3 w-1/3" />
          <Skeleton className="h-3 w-3/4" />
        </div>
      ))}
    </div>
  )
}

export function AdoptionInboxPage() {
  const queryClient = useQueryClient()

  const { data: sent, isLoading: sentLoading } = useQuery({
    queryKey: ['myAdoptionRequests'],
    queryFn: getMyRequests,
  })

  const { data: received, isLoading: receivedLoading } = useQuery({
    queryKey: ['receivedAdoptionRequests'],
    queryFn: getReceivedRequests,
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: AdoptionStatus }) =>
      updateAdoptionStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['myAdoptionRequests'] })
      queryClient.invalidateQueries({ queryKey: ['receivedAdoptionRequests'] })
      toast({ title: 'Status updated' })
    },
    onError: () => {
      toast({ title: 'Update failed', variant: 'destructive' })
    },
  })

  function handleAction(id: string, status: AdoptionStatus) {
    updateMutation.mutate({ id, status })
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <h1 className="text-2xl font-bold">Adoption Inbox</h1>

      <Tabs defaultValue="sent">
        <TabsList>
          <TabsTrigger value="sent">
            My Requests{sent ? ` (${sent.length})` : ''}
          </TabsTrigger>
          <TabsTrigger value="received">
            Received{received ? ` (${received.length})` : ''}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="sent" className="space-y-3">
          {sentLoading ? (
            <SkeletonCards />
          ) : !sent || sent.length === 0 ? (
            <div className="py-10 text-center text-muted-foreground">
              <p className="text-3xl mb-2">🐾</p>
              <p>You haven't sent any adoption requests yet.</p>
              <Button asChild variant="link" className="mt-2">
                <Link to="/pets">Browse pets</Link>
              </Button>
            </div>
          ) : (
            sent.map((req) => (
              <RequestCard
                key={req.id}
                req={req}
                role="sent"
                onAction={handleAction}
                isPending={updateMutation.isPending}
              />
            ))
          )}
        </TabsContent>

        <TabsContent value="received" className="space-y-3">
          {receivedLoading ? (
            <SkeletonCards />
          ) : !received || received.length === 0 ? (
            <div className="py-10 text-center text-muted-foreground">
              <p className="text-3xl mb-2">📬</p>
              <p>No adoption requests received yet.</p>
            </div>
          ) : (
            received.map((req) => (
              <RequestCard
                key={req.id}
                req={req}
                role="received"
                onAction={handleAction}
                isPending={updateMutation.isPending}
              />
            ))
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}
