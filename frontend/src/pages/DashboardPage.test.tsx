import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { DashboardPage } from './DashboardPage'
import { deletePet, getMyPets } from '@/lib/pets'
import type { PetSummary } from '@/types'

vi.mock('@/lib/pets', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/pets')>()
  return { ...actual, getMyPets: vi.fn(), deletePet: vi.fn() }
})
vi.mock('@/lib/notify', () => ({
  notify: { success: vi.fn(), error: vi.fn() },
}))

const activePet: PetSummary = {
  id: 'p1',
  title: 'Golden retriever puppy',
  species: 'DOG',
  breed: null,
  price: '8500.00',
  city: 'Quezon City',
  province: 'Metro Manila',
  sex: 'MALE',
  status: 'ACTIVE',
  createdAt: '2026-10-20T10:00:00Z',
  coverImageUrl: '/img/active.jpg',
}

const soldPet: PetSummary = {
  ...activePet,
  id: 'p2',
  title: 'Shih Tzu, 1 year old',
  status: 'SOLD',
  coverImageUrl: '/img/sold.jpg',
}

function renderDashboard() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/pets/new" element={<p>Create stub</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.mocked(getMyPets).mockReset()
  vi.mocked(deletePet).mockReset()
  vi.mocked(getMyPets).mockResolvedValue({
    content: [activePet, soldPet],
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 12,
  })
})

describe('DashboardPage', () => {
  it('greys out the photo of a sold listing but keeps active ones normal', async () => {
    renderDashboard()

    const sold = await screen.findByRole('img', { name: 'Shih Tzu, 1 year old' })
    expect(sold).toHaveClass('grayscale')

    const active = screen.getByRole('img', { name: 'Golden retriever puppy' })
    expect(active).not.toHaveClass('grayscale')
  })

  it('labels every listing with its status', async () => {
    renderDashboard()

    expect(await screen.findByText('ACTIVE')).toBeInTheDocument()
    expect(screen.getByText('SOLD')).toBeInTheDocument()
  })
})
