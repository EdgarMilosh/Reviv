import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import { RequireAuth, RequireConsentPending } from './auth/RouteGuards'
import LoginPage from './pages/LoginPage'
import ConsentPage from './pages/ConsentPage'
import HomePage from './pages/HomePage'
import Placeholder from './pages/Placeholder'
import UploadPage from './pages/UploadPage'
import ProfilePage from './pages/ProfilePage'


export default function App() {
    return (
        <Routes>
            <Route path="/login" element={<LoginPage />} />

            <Route
                path="/consent"
                element={
                    <RequireConsentPending>
                        <ConsentPage />
                    </RequireConsentPending>
                }
            />

            <Route
                path="/"
                element={
                    <RequireAuth>
                        <Layout />
                    </RequireAuth>
                }
            >
                <Route index element={<HomePage />} />
                <Route path="upload" element={<UploadPage />} />
                <Route path="profile" element={<ProfilePage />} />
                <Route
                    path="reports"
                    element={
                        <RequireAuth roles={['ADMIN']}>
                            <Placeholder title="Ataskaitos" />
                        </RequireAuth>
                    }
                />
                <Route
                    path="users"
                    element={
                        <RequireAuth roles={['ADMIN']}>
                            <Placeholder title="Naudotojai" />
                        </RequireAuth>
                    }
                />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    )
}