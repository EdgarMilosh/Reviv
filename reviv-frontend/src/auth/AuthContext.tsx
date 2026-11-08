import {
    createContext,
    useCallback,
    useContext,
    useEffect,
    useState,
    type ReactNode,
} from 'react'
import axios from 'axios'
import { api } from '../api'
import type { GoogleProfile, User } from '../types'

export type AuthStatus = 'loading' | 'anonymous' | 'unregistered' | 'registered'

interface AuthState {
    status: AuthStatus
    user: User | null // set only when status = 'registered'
    profile: GoogleProfile | null // name, email, picture from Google
    loginWithGoogle: (credential: string) => Promise<AuthStatus>
    acceptConsent: () => Promise<void>
    logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

// Decodes the Google ID token payload (signature is verified by the backend)
function decodeJwt(token: string): (GoogleProfile & { exp?: number }) | null {
    try {
        const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
        const json = decodeURIComponent(
            atob(payload)
                .split('')
                .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
                .join(''),
        )
        return JSON.parse(json)
    } catch {
        return null
    }
}

export function AuthProvider({ children }: { children: ReactNode }) {
    const [status, setStatus] = useState<AuthStatus>('loading')
    const [user, setUser] = useState<User | null>(null)
    const [profile, setProfile] = useState<GoogleProfile | null>(null)

    const clear = useCallback(() => {
        localStorage.removeItem('token')
        setUser(null)
        setProfile(null)
        setStatus('anonymous')
    }, [])

    // Uses the Google token to find out whether the user is already registered
    const resolve = useCallback(
        async (token: string): Promise<AuthStatus> => {
            const claims = decodeJwt(token)
            if (!claims || (claims.exp && claims.exp * 1000 < Date.now())) {
                clear() // token invalid or expired (Google tokens last ~1 h)
                return 'anonymous'
            }
            setProfile({
                email: claims.email,
                name: claims.name,
                picture: claims.picture,
            })
            try {
                const res = await api.get<User>('/api/users/me')
                setUser(res.data)
                setStatus('registered')
                return 'registered'
            } catch (e) {
                if (axios.isAxiosError(e) && e.response?.status === 404) {
                    setUser(null)
                    setStatus('unregistered')
                    return 'unregistered'
                }
                throw e
            }
        },
        [clear],
    )

    // On page load, restore the session if a token exists
    useEffect(() => {
        const token = localStorage.getItem('token')
        if (!token) {
            setStatus('anonymous')
            return
        }
        resolve(token).catch(clear)
    }, [resolve, clear])

    const loginWithGoogle = async (credential: string) => {
        localStorage.setItem('token', credential)
        try {
            return await resolve(credential)
        } catch (e) {
            clear()
            throw e
        }
    }

    const acceptConsent = async () => {
        const res = await api.post<User>('/api/users/register', { consent: true })
        setUser(res.data)
        setStatus('registered')
    }

    return (
        <AuthContext.Provider
            value={{ status, user, profile, loginWithGoogle, acceptConsent, logout: clear }}
        >
            {children}
        </AuthContext.Provider>
    )
}

export function useAuth() {
    const ctx = useContext(AuthContext)
    if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
    return ctx
}