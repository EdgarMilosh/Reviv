import { useAuth } from '../auth/auth-Context'
export default function HomePage() {
    const { user } = useAuth()
    return (
        <>
            <h1>Sveiki, {user?.name}!</h1>
            <p className="muted">
                Esate prisijungęs kaip <strong>{user?.role}</strong> ({user?.email}).
            </p>
        </>
    )
}