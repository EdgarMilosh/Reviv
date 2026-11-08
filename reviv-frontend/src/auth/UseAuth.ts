import { createContext, useContext } from 'react'
import type { GoogleProfile, User } from '../types'

export type AuthStatus = 'loading' | 'anonymous' | 'unregistered' | 'registered'

export interface AuthState {
    status: AuthStatus
    user: User | null // set only when status = 'registered'
    profile: GoogleProfile | null // name, email, picture from Google
    loginWithGoogle: (credential: string) => Promise<AuthStatus>
    acceptConsent: () => Promise<void>
    logout: () => void
}

export const UseAuth = createContext<AuthState | null>(null)

export function useAuth() {
    const ctx = useContext(UseAuth)
    if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
    return ctx
}