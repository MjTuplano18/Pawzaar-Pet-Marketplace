import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { BrowsePage } from './BrowsePage'
import { listPets } from '@/lib/pets'
import type { PetSummary } from '@/types'

vi.mock('@/lib/pets', () => ({ listPets: vi.fn() }))

const pet: PetSummary = {
  id: 'p1',
  title: 'Golden retriever puppy',
  species: 'DOG',
  breed: 'Golden retriever',
  price: '8500.00',
  city: 'Quezon City',
  province: 'Metro Manila',
  sex: 'MALE',
  status: 'ACTIVE',
  createdAt: '2026-10-20T10:00:00Z',
  coverImageUrl: null,
}

function mockListingPage(totalPages = 1) {
  return {
    content: [pet],
    totalElements: 1,
    totalPages,
    page: 0,
    size: 12,
  }
}

function renderBrowse(initialUrl = '/browse') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialUrl]}>
        <Routes>
          <Route path="/browse" element={<BrowsePage />} />
          <Route path="/pets/:id" element={<p>Pet detail stub</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.mocked(listPets).mockReset()
  vi.mocked(listPets).mockResolvedValue(mockListingPage())
})

describe('BrowsePage filters', () => {
  it('shows listings returned by the API', async () => {
    renderBrowse()

    expect(await screen.findByText('Golden retriever puppy')).toBeInTheDocument()
    expect(screen.getByText('1 listing')).toBeInTheDocument()
  })

  it('sends the species filter from the URL to the API', async () => {
    renderBrowse('/browse?species=DOG')

    await screen.findByText('Golden retriever puppy')
    expect(listPets).toHaveBeenCalledWith(expect.objectContaining({ species: 'DOG' }))
  })

  it('a typed city filter lands in the URL after the debounce', async () => {
    const user = userEvent.setup()
    renderBrowse()

    await screen.findByText('Golden retriever puppy')
    await user.type(screen.getByPlaceholderText('City'), 'Cebu')

    await waitFor(() =>
      expect(listPets).toHaveBeenLastCalledWith(expect.objectContaining({ city: 'Cebu' }))
    )
  })

  it('clearing filters drops the city from the query', async () => {
    const user = userEvent.setup()
    renderBrowse('/browse?city=Cebu')

    await screen.findByText('Golden retriever puppy')
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))

    await waitFor(() => expect(listPets).toHaveBeenLastCalledWith(expect.not.objectContaining({ city: expect.anything() })))
  })

  it('warns when the minimum price is above the maximum', async () => {
    const first = renderBrowse('/browse?minPrice=500&maxPrice=100')

    expect(await screen.findByText('Minimum price is higher than the maximum price.')).toBeInTheDocument()
    first.unmount()

    // A sane range shows no warning
    renderBrowse('/browse?minPrice=100&maxPrice=500')
    await screen.findByText('Golden retriever puppy')
    expect(screen.queryByText('Minimum price is higher than the maximum price.')).not.toBeInTheDocument()
  })
})
