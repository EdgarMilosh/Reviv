import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from './auth-Context'
import type { Role } from '../types'

// Reikia būti prisijungusiam IR užregistruotam (+ pasirinktinai rolė)
export function RequireAuth({
  children,
  roles,
}: {
  children: ReactNode
  roles?: Role[]
}) {
  const { status, user } = useAuth()

  if (status === 'loading') return <p className="center">Kraunama…</p>
  if (status === 'anonymous') return <Navigate to="/login" replace />
  if (status === 'unregistered') return <Navigate to="/consent" replace />
  if (roles && user && !roles.includes(user.role)) return <Navigate to="/" replace />

  return <>{children}</>
}

// Sutikimo puslapis: prisijungęs, bet dar neužregistruotas
export function RequireConsentPending({ children }: { children: ReactNode }) {
  const { status } = useAuth()

  if (status === 'loading') return <p className="center">Kraunama…</p>
  if (status === 'anonymous') return <Navigate to="/login" replace />
  if (status === 'registered') return <Navigate to="/" replace />

  return <>{children}</>
}