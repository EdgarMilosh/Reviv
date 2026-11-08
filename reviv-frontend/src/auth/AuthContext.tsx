import {
    useCallback,
    useEffect,
    useState,
    type ReactNode,
} from 'react'
import axios from 'axios'
import { api } from '../api'
import type { GoogleProfile, User } from '../types'
import { AuthContext, type AuthStatus } from './auth-Context'

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
    // No token means anonymous right away, so no setState is needed in the effect
    const [status, setStatus] = useState<AuthStatus>(() =>
        localStorage.getItem('token') ? 'loading' : 'anonymous',
    )
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
        if (!token) return

        let cancelled = false
        const restore = async () => {
            try {
                await resolve(token)
            } catch {
                if (!cancelled) clear()
            }
        }
        void restore()

        return () => {
            cancelled = true
        }
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