import { useEffect, useState } from 'react'

/**
 * Returns a copy of `value` that only changes after it has stayed the same for
 * `delayMs`. Used so typed filter inputs (city, province, price…) don't fire an
 * API request on every keystroke.
 */
export function useDebounced<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}
