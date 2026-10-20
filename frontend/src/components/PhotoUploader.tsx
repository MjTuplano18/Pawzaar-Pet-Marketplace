import { useRef, useState } from 'react'
import type { ChangeEvent } from 'react'
import { ImagePlus, Loader2 } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { deletePetImage, uploadPetImage, MAX_PHOTOS_PER_LISTING } from '@/lib/pets'
import { notify } from '@/lib/notify'
import type { PetImageResponse } from '@/types'

/**
 * The shared photo manager for the create and edit listing flows.
 *
 * Buyers judge a listing by its photos, so this is deliberately generous with itself:
 * multi-select drag-in from the plain file dialog, sequential uploads that keep going
 * when one file fails, and a visible counter so the 4-photo cap is never a surprise.
 * The first photo is the cover shown on cards and tiles.
 */
export function PhotoUploader({ petId, images, onImagesChange }: {
  petId: string
  images: PetImageResponse[]
  onImagesChange: (images: PetImageResponse[]) => void
}) {
  const fileRef = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const remaining = MAX_PHOTOS_PER_LISTING - images.length

  async function handleFilesSelected(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    if (fileRef.current) fileRef.current.value = ''
    if (files.length === 0 || remaining <= 0) return

    if (files.length > remaining) {
      notify.error(
        `Up to ${MAX_PHOTOS_PER_LISTING} photos per listing`,
        `Keeping the first ${remaining} of your ${files.length} files.`,
      )
    }

    const chosen = files.slice(0, remaining)
    setBusy(true)
    const uploaded: PetImageResponse[] = []
    for (const file of chosen) {
      try {
        uploaded.push(await uploadPetImage(petId, file))
      } catch {
        // One bad file should not cost the rest of the batch; report it and continue.
        notify.error('Some files could not be uploaded', `"${file.name}" was skipped. Common causes: not an image or too large.`)
      }
    }
    setBusy(false)

    const failed = chosen.length - uploaded.length
    if (failed === 0) {
      notify.success(chosen.length === 1 ? 'Photo added' : `Added ${chosen.length} photos`)
    }
    if (uploaded.length > 0) onImagesChange([...images, ...uploaded])
  }

  async function handleRemove(imageId: string) {
    setDeletingId(imageId)
    try {
      await deletePetImage(petId, imageId)
      onImagesChange(images.filter(i => i.id !== imageId))
      notify.success('Photo removed')
    } catch {
      notify.error('Could not remove photo')
    } finally {
      setDeletingId(null)
    }
  }

  const atCap = remaining <= 0

  return (
    <div className="space-y-3">
      {images.length > 0 && (
        <div className="flex flex-wrap gap-3">
          {images.map((img, i) => (
            <div key={img.id} className="group relative">
              <img
                src={img.url}
                alt={`Listing photo ${i + 1}`}
                className="h-24 w-24 rounded-xl object-cover ring-1 ring-ring/10"
              />
              {i === 0 && (
                <span className="absolute inset-x-1 bottom-1 rounded-md bg-black/60 px-1.5 py-0.5 text-center text-[10px] font-semibold text-white">
                  Cover
                </span>
              )}
              <button
                type="button"
                onClick={() => handleRemove(img.id)}
                disabled={busy || deletingId !== null}
                aria-label={`Remove photo ${i + 1}`}
                className="absolute -top-2 -right-2 flex h-6 w-6 items-center justify-center rounded-full bg-destructive text-lg leading-none text-destructive-foreground shadow-sm hover:bg-destructive/90 disabled:opacity-50"
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}

      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        multiple
        className="sr-only"
        onChange={handleFilesSelected}
        disabled={busy || atCap}
      />

      <div className="flex items-center gap-3">
        <Button
          type="button"
          variant="outline"
          onClick={() => fileRef.current?.click()}
          disabled={busy || atCap}
        >
          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ImagePlus className="h-4 w-4" />}
          {busy ? 'Uploading…' : 'Add photos'}
        </Button>
        <span className="text-sm text-muted-foreground">
          {images.length} of {MAX_PHOTOS_PER_LISTING} photos
        </span>
      </div>

      <p className="text-xs text-muted-foreground">
        You can upload up to {MAX_PHOTOS_PER_LISTING} photos at once. Choose several files in the
        picker, or add more later.{' '}
        {atCap
          ? 'Photo limit reached — remove one to add another.'
          : 'The first photo becomes the cover buyers see in search.'}
      </p>
    </div>
  )
}
