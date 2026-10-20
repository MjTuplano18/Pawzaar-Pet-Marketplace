import { useState, useRef } from 'react'
import { Link } from 'react-router-dom'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field } from '@/components/Field'
import { useAuth } from '../auth/auth-context'
import { resendVerification } from '../lib/auth'
import { ApiError } from '../lib/api'
import { updateProfile, uploadAvatar, deleteAvatar } from '../lib/profile'
import { notify } from '@/lib/notify'

export function ProfilePage() {
  const { user, logout, reloadUser } = useAuth()
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // Edit profile state
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [phone, setPhone] = useState(user?.phone ?? '')
  const [bio, setBio] = useState(user?.bio ?? '')
  const [profileSaving, setProfileSaving] = useState(false)
  const [profileFieldErrors, setProfileFieldErrors] = useState<Record<string, string>>({})

  // Avatar state
  const [avatarUploading, setAvatarUploading] = useState(false)
  const [avatarKey, setAvatarKey] = useState(0)
  const avatarFileRef = useRef<HTMLInputElement>(null)

  if (!user) return null

  async function handleResend() {
    setNotice(null)
    setError(null)
    setBusy(true)
    try {
      await resendVerification()
      setNotice('If your email still needs verifying, a fresh link is on its way.')
    } catch (err) {
      setError(err instanceof ApiError ? err.detail || err.title : 'Could not send the email.')
    } finally {
      setBusy(false)
    }
  }

  async function handleRefresh() {
    setNotice(null)
    setError(null)
    try {
      await reloadUser()
      setNotice('Profile refreshed.')
    } catch (err) {
      setError(err instanceof ApiError ? err.detail || err.title : 'Could not refresh.')
    }
  }

  async function handleSaveProfile(e: React.FormEvent) {
    e.preventDefault()
    setProfileFieldErrors({})
    setProfileSaving(true)
    try {
      await updateProfile({
        displayName: displayName.trim(),
        phone: phone.trim() || undefined,
        bio: bio.trim() || undefined,
      })
      await reloadUser()
      notify.success('Profile saved!')
    } catch (err) {
      if (err instanceof ApiError) {
        const next: Record<string, string> = {}
        for (const fe of err.fieldErrors) next[fe.field] = fe.message
        setProfileFieldErrors(next)
        notify.error(err.detail || err.title)
      } else {
        notify.error('Could not save profile.')
      }
    } finally {
      setProfileSaving(false)
    }
  }

  async function handleAvatarUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setAvatarUploading(true)
    try {
      await uploadAvatar(file)
      await reloadUser()
      setAvatarKey(k => k + 1)
      notify.success('Avatar updated!')
    } catch {
      notify.error('Upload failed', 'Could not upload your avatar.')
    } finally {
      setAvatarUploading(false)
      if (avatarFileRef.current) avatarFileRef.current.value = ''
    }
  }

  async function handleDeleteAvatar() {
    try {
      await deleteAvatar()
      await reloadUser()
      setAvatarKey(k => k + 1)
      notify.success('Avatar removed')
    } catch {
      notify.error('Could not remove avatar')
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-extrabold tracking-tight">Your profile</h1>

      {notice && (
        <Alert className="border-green-200 bg-green-50 text-green-800">
          <AlertDescription>{notice}</AlertDescription>
        </Alert>
      )}
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* Account details */}
      <Card>
        <CardHeader><CardTitle>Account details</CardTitle></CardHeader>
        <CardContent>
          <dl className="grid grid-cols-[160px_1fr] gap-x-4 gap-y-3 text-sm">
            <dt className="font-medium text-muted-foreground">Display name</dt>
            <dd>{user.displayName}</dd>
            <dt className="font-medium text-muted-foreground">Email</dt>
            <dd className="flex items-center gap-2">
              {user.email}
              {user.verified ? (
                <Badge variant="success">verified</Badge>
              ) : (
                <Badge variant="warning">unverified</Badge>
              )}
            </dd>
            <dt className="font-medium text-muted-foreground">Phone</dt>
            <dd>{user.phone ?? <span className="text-muted-foreground">—</span>}</dd>
            <dt className="font-medium text-muted-foreground">Role</dt>
            <dd>{user.role}</dd>
            <dt className="font-medium text-muted-foreground">Member since</dt>
            <dd>{new Date(user.createdAt).toLocaleDateString()}</dd>
          </dl>
        </CardContent>
      </Card>

      {/* Avatar section */}
      <Card>
        <CardHeader><CardTitle>Avatar</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center gap-4">
            {user.avatarUrl ? (
              <img
                key={avatarKey}
                src={`/api/v1/me/avatar?v=${avatarKey}`}
                alt="Your avatar"
                className="h-20 w-20 rounded-full object-cover border"
              />
            ) : (
              <div className="h-20 w-20 rounded-full bg-secondary flex items-center justify-center text-xl font-bold border text-secondary-foreground">
                {user.displayName.trim().charAt(0).toUpperCase()}
              </div>
            )}
            <div className="space-y-2">
              <div className="flex gap-2">
                <Button size="sm" variant="outline" onClick={() => avatarFileRef.current?.click()} disabled={avatarUploading}>
                  {avatarUploading ? 'Uploading…' : 'Upload avatar'}
                </Button>
                {user.avatarUrl && (
                  <Button size="sm" variant="ghost" onClick={handleDeleteAvatar}>Remove</Button>
                )}
              </div>
              <input
                ref={avatarFileRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={handleAvatarUpload}
              />
              <p className="text-xs text-muted-foreground">JPG, PNG or GIF. Max 5 MB.</p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Edit profile */}
      <Card>
        <CardHeader><CardTitle>Edit profile</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={handleSaveProfile} noValidate className="space-y-3">
            <Field
              id="displayName"
              label="Display name"
              value={displayName}
              onChange={setDisplayName}
              error={profileFieldErrors.displayName}
              required
            />
            <Field
              id="phone"
              label="Phone (optional)"
              type="tel"
              value={phone ?? ''}
              onChange={setPhone}
              error={profileFieldErrors.phone}
              hint="e.g. +639171234567"
            />
            <div className="space-y-1">
              <label htmlFor="bio" className="text-sm font-medium">Bio (optional)</label>
              <textarea
                id="bio"
                value={bio ?? ''}
                onChange={(e) => setBio(e.target.value)}
                placeholder="Tell others a little about yourself…"
                rows={3}
                className="flex min-h-[80px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-base shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
              />
              {profileFieldErrors.bio && <p className="text-xs text-destructive">{profileFieldErrors.bio}</p>}
            </div>
            <Button type="submit" disabled={profileSaving}>
              {profileSaving ? 'Saving…' : 'Save changes'}
            </Button>
          </form>
        </CardContent>
      </Card>

      {!user.verified && (
        <Alert>
          <AlertDescription>Your email isn't verified yet. Check your inbox, or request a new link.</AlertDescription>
        </Alert>
      )}

      <div className="flex flex-wrap gap-3">
        {!user.verified && (
          <Button onClick={handleResend} disabled={busy}>
            {busy ? 'Sending…' : 'Resend verification email'}
          </Button>
        )}
        <Button variant="secondary" onClick={handleRefresh}>Refresh</Button>
        <Button variant="ghost" onClick={logout}>Log out</Button>
      </div>

      <p className="text-sm">
        <Link to="/" className="underline underline-offset-4 hover:text-foreground text-muted-foreground">Back home</Link>
      </p>
    </div>
  )
}
