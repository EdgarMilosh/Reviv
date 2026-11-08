import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { GoogleLogin } from '@react-oauth/google'
import { useAuth } from '../auth/UseAuth'


export default function LoginPage() {
  const { status, loginWithGoogle } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  if (status === 'registered') return <Navigate to="/" replace />
  if (status === 'unregistered') return <Navigate to="/consent" replace />

  return (
    <div className="center-page">
      <div className="card">
        <h1>Reviv</h1>
        <p className="muted">Prisijunkite naudodami Google paskyrą</p>

        <div className="google-btn">
          <GoogleLogin
            onSuccess={async (response) => {
              if (!response.credential) {
                setError('Google negrąžino prisijungimo duomenų.')
                return
              }
              try {
                setError(null)
                const s = await loginWithGoogle(response.credential)
                navigate(s === 'registered' ? '/' : '/consent', { replace: true })
              } catch {
                setError('Nepavyko prisijungti. Patikrinkite, ar veikia backend.')
              }
            }}
            onError={() => setError('Prisijungimas su Google nepavyko.')}
          />
        </div>

        {error && <p className="error">{error}</p>}
      </div>
    </div>
  )
}