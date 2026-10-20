import { toast } from '@/hooks/use-toast'

/**
 * The single entry point for the app's notification bar (top-level toasts).
 *
 * Every success/failure feedback goes through `notify` so titles, wording, icons and
 * duration stay consistent: action pages never hand-roll `toast({...})` themselves.
 */
export const notify = {
  success(title: string, description?: string) {
    toast({ title, description })
  },
  error(title: string, description?: string) {
    toast({ title, description, variant: 'destructive' })
  },
}
