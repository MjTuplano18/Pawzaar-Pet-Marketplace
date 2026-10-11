import type { ChangeEvent } from 'react'

import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

interface FieldProps {
  id: string
  label: string
  type?: string
  value: string
  onChange: (value: string) => void
  error?: string
  hint?: string
  autoComplete?: string
  required?: boolean
  placeholder?: string
}

export function Field({
  id,
  label,
  type = 'text',
  value,
  onChange,
  error,
  hint,
  autoComplete,
  required,
  placeholder,
}: FieldProps) {
  function handleChange(e: ChangeEvent<HTMLInputElement>) {
    onChange(e.target.value)
  }

  return (
    <div className="mb-4 space-y-1.5">
      <Label htmlFor={id}>{label}{required && <span className="text-destructive ml-0.5">*</span>}</Label>
      <Input
        id={id}
        name={id}
        type={type}
        value={value}
        onChange={handleChange}
        autoComplete={autoComplete}
        required={required}
        placeholder={placeholder}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        className={cn(error && 'border-destructive focus-visible:ring-destructive')}
      />
      {hint && !error && (
        <p id={`${id}-hint`} className="text-xs text-muted-foreground">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="text-xs text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
