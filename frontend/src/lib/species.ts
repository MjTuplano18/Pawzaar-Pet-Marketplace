import type { LucideIcon } from 'lucide-react'
import { Cat, Dog, PawPrint } from 'lucide-react'

import type { Species } from '@/types'

/** Icon + friendly label for a species, used everywhere listings are shown. */
export const SPECIES_META: Record<Species, { label: string; Icon: LucideIcon }> = {
  DOG: { label: 'Dog', Icon: Dog },
  CAT: { label: 'Cat', Icon: Cat },
  OTHER: { label: 'Other', Icon: PawPrint },
}

export function speciesMeta(species: string): { label: string; Icon: LucideIcon } {
  return SPECIES_META[species as Species] ?? { label: 'Pet', Icon: PawPrint }
}
