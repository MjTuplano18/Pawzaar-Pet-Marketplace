import { request } from './api'
import type { AdoptionRequestResponse, AdoptionStatus } from '../types'

/**
 * All adoption-request API calls, mirroring AdoptionController on the backend.
 * Every call uses `auth: true` — all adoption endpoints require authentication.
 */

/** Submit a new adoption request for a pet. */
export function createAdoptionRequest(
  petId: string,
  message?: string,
): Promise<AdoptionRequestResponse> {
  return request<AdoptionRequestResponse>(`/api/v1/pets/${petId}/adoption-requests`, {
    method: 'POST',
    body: { message: message ?? null },
    auth: true,
  })
}

/** All adoption requests for a specific pet (pet owner only). */
export function getRequestsForPet(petId: string): Promise<AdoptionRequestResponse[]> {
  return request<AdoptionRequestResponse[]>(`/api/v1/pets/${petId}/adoption-requests`, {
    auth: true,
  })
}

/** All adoption requests submitted by the authenticated user. */
export function getMyRequests(): Promise<AdoptionRequestResponse[]> {
  return request<AdoptionRequestResponse[]>('/api/v1/me/adoption-requests', { auth: true })
}

/** All requests received across every pet the authenticated seller owns. */
export function getReceivedRequests(): Promise<AdoptionRequestResponse[]> {
  return request<AdoptionRequestResponse[]>('/api/v1/me/received-requests', { auth: true })
}

/** Update the status of a specific adoption request (APPROVE / REJECT / WITHDRAW). */
export function updateAdoptionStatus(
  requestId: string,
  status: AdoptionStatus,
): Promise<AdoptionRequestResponse> {
  return request<AdoptionRequestResponse>(`/api/v1/adoption-requests/${requestId}`, {
    method: 'PATCH',
    body: { status },
    auth: true,
  })
}

/** Fetch a single adoption request by id. */
export function getAdoptionRequest(requestId: string): Promise<AdoptionRequestResponse> {
  return request<AdoptionRequestResponse>(`/api/v1/adoption-requests/${requestId}`, { auth: true })
}
