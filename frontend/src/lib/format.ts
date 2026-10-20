/** Formatting helpers shared by the listing pages. */

/** PHP price formatting: a "12000.00" string becomes "₱12,000". */
export function formatPrice(price: string): string {
  return `₱${Number(price).toLocaleString('en-PH')}`
}

/** Age in months → "8 months", "1 yr 4 mo". */
export function formatAge(months: number): string {
  if (months < 1) return '< 1 month old'
  if (months < 12) return `${months} month${months === 1 ? '' : 's'} old`
  const years = Math.floor(months / 12)
  const remainder = months % 12
  const yearsPart = `${years} yr${years === 1 ? '' : 's'}`
  return remainder > 0 ? `${yearsPart} ${remainder} mo` : `${yearsPart} old`
}

/** Coarse "x ago" for list cards. Returns absolute date for anything older than 60 days. */
export function formatRelativeTime(iso: string): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return ''
  const secondsAgo = Math.max(0, Math.floor((Date.now() - then) / 1000))

  const minutes = Math.floor(secondsAgo / 60)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`

  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`

  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}d ago`
  if (days < 30) return `${Math.floor(days / 7)}w ago`
  if (days < 60) return `1mo ago`
  return new Date(iso).toLocaleDateString('en-PH', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

/** DB values may be stored UPPERCASE; show them capitalized instead ("MALE" → "Male"). */
export function formatSex(sex: string | null): string | null {
  if (!sex) return null
  return sex.charAt(0).toUpperCase() + sex.slice(1).toLowerCase()
}
