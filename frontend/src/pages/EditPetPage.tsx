import { useState, useEffect, useRef } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { Field } from '@/components/Field'
import { ApiError } from '@/lib/api'
import { getPet, updatePet, uploadPetImage, deletePetImage, type UpdatePetInput } from '@/lib/pets'
import { useAuth } from '@/auth/auth-context'
import { toast } from '@/hooks/use-toast'
import type { PetImageResponse } from '@/types'

export function EditPetPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const { data: pet, isLoading } = useQuery({
    queryKey: ['pet', id],
    queryFn: () => getPet(id!),
    enabled: !!id,
  })

  const [title, setTitle] = useState('')
  const [species, setSpecies] = useState('DOG')
  const [breed, setBreed] = useState('')
  const [ageMonths, setAgeMonths] = useState('')
  const [price, setPrice] = useState('')
  const [description, setDescription] = useState('')
  const [city, setCity] = useState('')
  const [province, setProvince] = useState('')
  const [sex, setSex] = useState('')
  const [status, setStatus] = useState('ACTIVE')
  const [images, setImages] = useState<PetImageResponse[]>(pet?.images ?? [])

  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [uploading, setUploading] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!pet) return
    if (user && user.id !== pet.sellerId) { navigate(`/pets/${pet.id}`, { replace: true }); return }
    setTitle(pet.title)
    setSpecies(pet.species)
    setBreed(pet.breed ?? '')
    setAgeMonths(String(pet.ageMonths))
    setPrice(pet.price)
    setDescription(pet.description ?? '')
    setCity(pet.city)
    setProvince(pet.province)
    setSex(pet.sex ?? '')
    setStatus(pet.status)
    setImages(pet.images)
  }, [pet, user, navigate])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      const body: UpdatePetInput = {
        title: title.trim(),
        species,
        ageMonths: Number(ageMonths),
        price: Number(price),
        city: city.trim(),
        province: province.trim(),
        status,
        ...(breed.trim() ? { breed: breed.trim() } : {}),
        ...(description.trim() ? { description: description.trim() } : {}),
        ...(sex ? { sex } : {}),
      }
      await updatePet(id!, body)
      queryClient.invalidateQueries({ queryKey: ['pet', id] })
      queryClient.invalidateQueries({ queryKey: ['pets'] })
      toast({ title: 'Listing saved!' })
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
    if (!file) return
    setUploading(true)
    try {
      const img = await uploadPetImage(id!, file)
      setImages(prev => [...prev, img])
      queryClient.invalidateQueries({ queryKey: ['pet', id] })
      toast({ title: 'Image uploaded' })
    } catch {
      toast({ title: 'Upload failed', variant: 'destructive' })
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  async function handleDeleteImage(imageId: string) {
    try {
      await deletePetImage(id!, imageId)
      setImages(prev => prev.filter(i => i.id !== imageId))
      queryClient.invalidateQueries({ queryKey: ['pet', id] })
    } catch {
      toast({ title: 'Could not delete image', variant: 'destructive' })
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-xl space-y-4">
        <Skeleton className="h-8 w-48" />
        {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
      </div>
    )
  }

  if (!pet) return null

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-extrabold tracking-tight">Edit Listing</h1>
        <Button variant="outline" onClick={() => navigate(`/pets/${id}`)}>View Listing</Button>
      </div>

      {/* Image management */}
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
        </CardContent>
      </Card>

      {/* Form */}
      <Card>
        <CardContent className="pt-6">
          {error && (
            <Alert variant="destructive" className="mb-4">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <Field id="title" label="Title" value={title} onChange={setTitle} error={fieldErrors.title} required />

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
            </div>

            <Field id="breed" label="Breed (optional)" value={breed} onChange={setBreed} error={fieldErrors.breed} />
            <Field id="ageMonths" label="Age (months)" type="number" value={ageMonths} onChange={setAgeMonths} error={fieldErrors.ageMonths} required />
            <Field id="price" label="Price (₱)" type="number" value={price} onChange={setPrice} error={fieldErrors.price} required />

            <div className="space-y-1">
              <label className="text-sm font-medium">Description (optional)</label>
              <Textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={4} />
            </div>

            <Field id="city" label="City" value={city} onChange={setCity} error={fieldErrors.city} required />
            <Field id="province" label="Province" value={province} onChange={setProvince} error={fieldErrors.province} required />

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

            <div className="space-y-1">
              <label className="text-sm font-medium">Status</label>
              <Select value={status} onValueChange={setStatus}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="ACTIVE">Active</SelectItem>
                  <SelectItem value="SOLD">Sold</SelectItem>
                  <SelectItem value="HIDDEN">Hidden</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? 'Saving…' : 'Save Changes'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
