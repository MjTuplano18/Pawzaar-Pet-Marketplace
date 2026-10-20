import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { CheckCircle2, ImagePlus } from 'lucide-react'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { Field } from '@/components/Field'
import { PhotoUploader } from '@/components/PhotoUploader'
import { ApiError } from '@/lib/api'
import { createPet, type CreatePetInput } from '@/lib/pets'
import type { PetResponse, PetImageResponse } from '@/types'
import { notify } from '@/lib/notify'

export function CreatePetPage() {
  const navigate = useNavigate()

  const [title, setTitle] = useState('')
  const [species, setSpecies] = useState('DOG')
  const [breed, setBreed] = useState('')
  const [ageMonths, setAgeMonths] = useState('')
  const [price, setPrice] = useState('')
  const [description, setDescription] = useState('')
  const [city, setCity] = useState('')
  const [province, setProvince] = useState('')
  const [sex, setSex] = useState('')

  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  const [createdPet, setCreatedPet] = useState<PetResponse | null>(null)
  const [images, setImages] = useState<PetImageResponse[]>([])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      const body: CreatePetInput = {
        title: title.trim(),
        species,
        ageMonths: Number(ageMonths),
        price: Number(price),
        city: city.trim(),
        province: province.trim(),
        ...(breed.trim() ? { breed: breed.trim() } : {}),
        ...(description.trim() ? { description: description.trim() } : {}),
        ...(sex ? { sex } : {}),
      }
      const pet = await createPet(body)
      setCreatedPet(pet)
      setImages(pet.images)
      notify.success('Listing created!', 'Now add photos so buyers can see it.')
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.detail || err.title)
        const next: Record<string, string> = {}
        for (const fe of err.fieldErrors) next[fe.field] = fe.message
        setFieldErrors(next)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (createdPet) {
    return (
      <div className="mx-auto max-w-xl space-y-6">
        <div className="space-y-3">
          <div className="flex items-center gap-4 text-sm">
            <span className="flex items-center gap-1.5 text-muted-foreground">
              <CheckCircle2 className="h-4 w-4 text-primary" strokeWidth={2} />
              Details
            </span>
            <span className="h-px flex-1 bg-border" />
            <span className="flex items-center gap-1.5 font-semibold text-foreground">
              <ImagePlus className="h-4 w-4 text-primary" />
              Photos
            </span>
          </div>
          <h1 className="text-2xl font-extrabold tracking-tight">Add photos</h1>
          <p className="text-muted-foreground">
            Listings with clear photos sell faster. Buyers see up to 4 photos.
          </p>
        </div>

        <Card>
          <CardHeader className="pb-2"><CardTitle>{createdPet.title}</CardTitle></CardHeader>
          <CardContent>
            <PhotoUploader
              petId={createdPet.id}
              images={images}
              onImagesChange={setImages}
            />
          </CardContent>
        </Card>

        <Button className="w-full" onClick={() => navigate(`/pets/${createdPet.id}`)}>
          Finish and preview listing
        </Button>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <h1 className="text-2xl font-extrabold tracking-tight">List a pet</h1>

      <Card>
        <CardContent className="pt-6">
          {error && (
            <Alert variant="destructive" className="mb-4">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <Field id="title" label="Title" value={title} onChange={setTitle} error={fieldErrors.title} required placeholder="e.g. Golden Retriever Puppy" />

            <div className="space-y-1">
              <label className="text-sm font-medium">Species</label>
              <Select value={species} onValueChange={setSpecies}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="DOG">Dog</SelectItem>
                  <SelectItem value="CAT">Cat</SelectItem>
                  <SelectItem value="OTHER">Other</SelectItem>
                </SelectContent>
              </Select>
              {fieldErrors.species && <p className="text-xs text-destructive">{fieldErrors.species}</p>}
            </div>

            <Field id="breed" label="Breed (optional)" value={breed} onChange={setBreed} error={fieldErrors.breed} placeholder="e.g. Labrador" />

            <Field id="ageMonths" label="Age (months)" type="number" value={ageMonths} onChange={setAgeMonths} error={fieldErrors.ageMonths} required placeholder="e.g. 3" />

            <Field id="price" label="Price (₱)" type="number" value={price} onChange={setPrice} error={fieldErrors.price} required placeholder="e.g. 5000" />

            <div className="space-y-1">
              <label className="text-sm font-medium">Description (optional)</label>
              <Textarea
                id="description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Tell buyers about this pet…"
                rows={4}
              />
              {fieldErrors.description && <p className="text-xs text-destructive">{fieldErrors.description}</p>}
            </div>

            <Field id="city" label="City" value={city} onChange={setCity} error={fieldErrors.city} required placeholder="e.g. Cebu City" />

            <Field id="province" label="Province" value={province} onChange={setProvince} error={fieldErrors.province} required placeholder="e.g. Cebu" />

            <div className="space-y-1">
              <label className="text-sm font-medium">Sex (optional)</label>
              <Select value={sex || 'UNSPECIFIED'} onValueChange={(v) => setSex(v === 'UNSPECIFIED' ? '' : v)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="UNSPECIFIED">Not specified</SelectItem>
                  <SelectItem value="MALE">Male</SelectItem>
                  <SelectItem value="FEMALE">Female</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? 'Creating…' : 'Create Listing'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
