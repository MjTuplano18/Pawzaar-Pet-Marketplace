import { Sun, Moon } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'

const STORAGE_KEY = 'pawzaar.theme' // must match the inline script in index.html

/**
 * Light/dark switch. The <html> element starts already set correctly by the
 * inline script in index.html, so this component only needs to flip it.
 */
export function ThemeToggle() {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains('dark'))

  function toggle() {
    const next = !dark
    setDark(next)
    document.documentElement.classList.toggle('dark', next)
    try {
      localStorage.setItem(STORAGE_KEY, next ? 'dark' : 'light')
    } catch {
      /* private mode can deny storage access; the toggle still works for this visit */
    }
  }

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={toggle}
      aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
    >
      {dark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
    </Button>
  )
}
