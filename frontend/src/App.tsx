import { Route, Routes } from 'react-router-dom'

import { ProtectedRoute } from './auth/ProtectedRoute'
import { Layout } from './components/Layout'
import { BrowsePage } from './pages/BrowsePage'
import { CreatePetPage } from './pages/CreatePetPage'
import { DashboardPage } from './pages/DashboardPage'
import { EditPetPage } from './pages/EditPetPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PetDetailPage } from './pages/PetDetailPage'
import { ProfilePage } from './pages/ProfilePage'
import { RegisterPage } from './pages/RegisterPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { VerifyEmailPage } from './pages/VerifyEmailPage'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/pets" element={<BrowsePage />} />

        {/* Protected routes — declared before /pets/:id so /pets/new is never treated as an id */}
        <Route element={<ProtectedRoute />}>
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/pets/new" element={<CreatePetPage />} />
          <Route path="/pets/:id/edit" element={<EditPetPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
        </Route>

        <Route path="/pets/:id" element={<PetDetailPage />} />

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
