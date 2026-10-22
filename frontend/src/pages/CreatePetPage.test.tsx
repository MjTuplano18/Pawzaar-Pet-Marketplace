import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/lib/api'
import { createPet } from '@/lib/pets'
import type { PetResponse, ProblemDetail } from '@/types'

import { CreatePetPage } from './CreatePetPage'

vi.mock('@/lib/pets', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/pets')>()
  return {
    ...actual,
    createPet: vi.fn(),
    uploadPetImage: vi.fn(),
    deletePetImage: vi.fn(),
  }
})
vi.mock('@/lib/notify', () => ({
  notify: { success: vi.fn(), error: vi.fn() },
}))

const createdPet: PetResponse = {
  id: 'p1',
  title: 'Golden retriever puppy',
  species: 'DOG',
  breed: null,
  ageMonths: 3,
  price: '8500.00',
  description: null,
  city: 'Quezon City',
  province: 'Metro Manila',
  sex: 'MALE',
  status: 'ACTIVE',
  createdAt: '2026-10-20T10:00:00Z',
  updatedAt: '2026-10-20T10:00:00Z',
  sellerId: 'u1',
  images: [],
}

function renderCreatePage() {
  return render(
    <MemoryRouter initialEntries={['/list-a-pet']}>
      <Routes>
        <Route path="/list-a-pet" element={<CreatePetPage />} />
        <Route path="/pets/:id" element={<p>Pet detail stub</p>} />
      </Routes>
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.mocked(createPet).mockReset()
})

describe('CreatePetPage', () => {
  it('routes the API validation errors onto the matching fields', async () => {
    const user = userEvent.setup()
    const problem = new ApiError(400, {
      status: 400,
      title: 'Validation failed',
      detail: 'Please fix the highlighted fields.',
      errors: [
        { field: 'title', message: 'Title is required.' },
        { field: 'price', message: 'Price must be a positive number.' },
      ],
    } as ProblemDetail)
    vi.mocked(createPet).mockRejectedValue(problem)

    renderCreatePage()
    await user.click(await screen.findByRole('button', { name: 'Create Listing' }))

    expect(await screen.findByText('Title is required.')).toBeInTheDocument()
    expect(screen.getByText('Price must be a positive number.')).toBeInTheDocument()
    expect(screen.getAllByText('List a pet').length).toBeGreaterThanOrEqual(1)
  })

  it('moves to the photo step after a successful create', async () => {
    const user = userEvent.setup()
    vi.mocked(createPet).mockResolvedValue(createdPet)

    renderCreatePage()
    await user.click(await screen.findByRole('button', { name: 'Create Listing' }))

    expect(await screen.findByRole('heading', { name: 'Add photos' })).toBeInTheDocument()
    expect(createPet).toHaveBeenCalledWith(expect.objectContaining({ title: '', species: 'DOG' }))
  })
})
