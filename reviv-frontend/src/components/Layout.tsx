import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import type { Role } from '../types'

interface MenuItem {
    to: string
    label: string
    roles: Role[]
}

const ALL: Role[] = ['USER', 'ADMIN']

// Čia valdoma, kas ką mato. Pridėti naują punktą = pridėti eilutę.
export const MENU: MenuItem[] = [
    { to: '/', label: 'Pradžia', roles: ALL },
    { to: '/reports', label: 'Ataskaitos', roles: ['ADMIN'] },
    { to: '/users', label: 'Naudotojai', roles: ['ADMIN'] },
    { to: '/profile', label: 'Mano profilis', roles: ALL },
]

export default function Layout() {
    const { user, profile, logout } = useAuth()
    const navigate = useNavigate()

    if (!user) return null

    const items = MENU.filter((item) => item.roles.includes(user.role))

    return (
        <div className="layout">
            <aside className="sidebar">
                <div className="brand">Reviv</div>

                <nav>
                    {items.map((item) => (
                        <NavLink
                            key={item.to}
                            to={item.to}
                            end={item.to === '/'}
                            className={({ isActive }) => (isActive ? 'nav active' : 'nav')}
                        >
                            {item.label}
                        </NavLink>
                    ))}
                </nav>

                <div className="user-box">
                    {profile?.picture && (
                        <img src={profile.picture} alt="" referrerPolicy="no-referrer" />
                    )}
                    <div>
                        <strong>{user.name || profile?.name}</strong>
                        <div className="muted small">{user.role}</div>
                    </div>
                    <button
                        className="btn secondary small"
                        onClick={() => {
                            logout()
                            navigate('/login', { replace: true })
                        }}
                    >
                        Atsijungti
                    </button>
                </div>
            </aside>

            <main className="content">
                <Outlet />
            </main>
        </div>
    )
}