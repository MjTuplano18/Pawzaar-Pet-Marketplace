import { useState, useRef } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { Field } from '@/components/Field'
import { ApiError } from '@/lib/api'
import { createPet, uploadPetImage, deletePetImage, type CreatePetInput } from '@/lib/pets'
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
  const [uploading, setUploading] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

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
      notify.success('Listing created!', 'You can now add images.')
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

  async function handleUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file || !createdPet) return
    setUploading(true)
    try {
      const img = await uploadPetImage(createdPet.id, file)
      setImages(prev => [...prev, img])
      notify.success('Image uploaded')
    } catch {
      notify.error('Upload failed')
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  async function handleDeleteImage(imageId: string) {
    if (!createdPet) return
    try {
      await deletePetImage(createdPet.id, imageId)
      setImages(prev => prev.filter(i => i.id !== imageId))
      notify.success('Image deleted')
    } catch {
      notify.error('Could not delete image')
    }
  }

  if (createdPet) {
    return (
      <div className="mx-auto max-w-xl space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-extrabold tracking-tight">Add Images</h1>
          <Button onClick={() => navigate(`/pets/${createdPet.id}`)}>View Listing →</Button>
        </div>

        <Card>
          <CardHeader><CardTitle>Photos</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            {images.length > 0 && (
              <div className="flex flex-wrap gap-3">
                {images.map(img => (
                  <div key={img.id} className="relative">
                    <img src={img.url} alt="" className="h-24 w-24 rounded-md object-cover border" />
                    <button
                      onClick={() => handleDeleteImage(img.id)}
                      className="absolute -top-1 -right-1 h-5 w-5 rounded-full bg-destructive text-white text-xs flex items-center justify-center hover:bg-destructive/90"
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
            )}
            <div className="flex items-center gap-3">
              <Input ref={fileRef} type="file" accept="image/*" onChange={handleUpload} disabled={uploading} className="max-w-xs" />
              {uploading && <span className="text-sm text-muted-foreground">Uploading…</span>}
            </div>
            <p className="text-xs text-muted-foreground">Upload images one at a time. First image becomes the cover.</p>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <h1 className="text-2xl font-extrabold tracking-tight">List a Pet</h1>

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
