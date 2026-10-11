import { getAccessToken, request } from './api'
import type { UserResponse } from '../types'

export function updateProfile(body: { displayName: string; phone?: string; bio?: string }): Promise<UserResponse> {
  return request<UserResponse>('/api/v1/me', { method: 'PUT', body, auth: true })
}

export async function uploadAvatar(file: File): Promise<UserResponse> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch('/api/v1/me/avatar', {
    method: 'POST',
    headers: { Authorization: `Bearer ${getAccessToken() ?? ''}` },
    body: form,
  })
  if (!res.ok) throw new Error('Upload failed')
  return res.json() as Promise<UserResponse>
}

export function deleteAvatar(): Promise<void> {
  return request<void>('/api/v1/me/avatar', { method: 'DELETE', auth: true })
}
