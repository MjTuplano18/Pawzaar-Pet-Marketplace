import { getAccessToken, request } from './api'
import type { PagedResponse, PetImageResponse, PetResponse, PetSummary } from '../types'

export interface PetFilters {
  page?: number; size?: number; species?: string; province?: string; city?: string
  breed?: string; minPrice?: number; maxPrice?: number; minAgeMonths?: number
  maxAgeMonths?: number; sort?: string; order?: string
}

export interface CreatePetInput {
  title: string; species: string; breed?: string; ageMonths: number; price: number
  description?: string; city: string; province: string; sex?: string
}

export interface UpdatePetInput extends CreatePetInput { status: string }

export function listPets(filters: PetFilters = {}): Promise<PagedResponse<PetSummary>> {
  const params = new URLSearchParams()
  Object.entries(filters).forEach(([k, v]) => { if (v !== undefined && v !== '') params.set(k, String(v)) })
  return request<PagedResponse<PetSummary>>(`/api/v1/pets?${params}`)
}

export function getPet(id: string): Promise<PetResponse> {
  return request<PetResponse>(`/api/v1/pets/${id}`)
}

export function createPet(body: CreatePetInput): Promise<PetResponse> {
  return request<PetResponse>('/api/v1/pets', { method: 'POST', body, auth: true })
}

export function updatePet(id: string, body: UpdatePetInput): Promise<PetResponse> {
  return request<PetResponse>(`/api/v1/pets/${id}`, { method: 'PUT', body, auth: true })
}

export function deletePet(id: string): Promise<void> {
  return request<void>(`/api/v1/pets/${id}`, { method: 'DELETE', auth: true })
}

export function getMyPets(page = 0, size = 20): Promise<PagedResponse<PetSummary>> {
  return request<PagedResponse<PetSummary>>(`/api/v1/me/pets?page=${page}&size=${size}`, { auth: true })
}

export async function uploadPetImage(petId: string, file: File): Promise<PetImageResponse> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch(`/api/v1/pets/${petId}/images`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${getAccessToken() ?? ''}` },
    body: form,
  })
  if (!res.ok) throw new Error('Upload failed')
  return res.json() as Promise<PetImageResponse>
}

export async function deletePetImage(petId: string, imageId: string): Promise<void> {
  await request<void>(`/api/v1/pets/${petId}/images/${imageId}`, { method: 'DELETE', auth: true })
}
