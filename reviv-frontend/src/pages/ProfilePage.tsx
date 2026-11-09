import { useEffect, useMemo, useState } from 'react'
import axios from 'axios'
import { api } from '../api'
import { useAuth } from '../auth/UseAuth'
import './ProfilePage.css'

const HARMFUL_HABITS = [
    'Rūkymas',
    'Alkoholio vartojimas',
    'Kofeinas',
    'Stresas',
    'Sėdimas gyvenimo būdas',
    'Per didelis ekrano naudojimas',
]

type ProfileResponse = {
    heightCm?: number | null
    weightKg?: number | null
    harmfulHabits?: string[]
    allergies?: string[]
    updatedAt?: string
}

type ProfileForm = {
    heightCm: string
    weightKg: string
    harmfulHabits: string[]
    allergies: string[]
    updatedAt: string
}

const emptyForm: ProfileForm = {
    heightCm: '',
    weightKg: '',
    harmfulHabits: [],
    allergies: [],
    updatedAt: '',
}

export default function ProfilePage() {
    const { user, profile } = useAuth()
    const [form, setForm] = useState<ProfileForm>(emptyForm)
    const [allergyInput, setAllergyInput] = useState('')
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [fieldError, setFieldError] = useState<{ heightCm?: boolean; weightKg?: boolean }>({})

    useEffect(() => {
        let active = true

        async function fetchProfile() {
            try {
                const response = await api.get<ProfileResponse>('/api/profile')
                if (!active) return

                const next: ProfileForm = {
                    heightCm: response.data.heightCm != null ? String(response.data.heightCm) : '',
                    weightKg: response.data.weightKg != null ? String(response.data.weightKg) : '',
                    harmfulHabits: response.data.harmfulHabits ?? [],
                    allergies: response.data.allergies ?? [],
                    updatedAt: response.data.updatedAt ?? '',
                }

                setForm(next)
            } catch (err) {
                if (!active) return
                if (axios.isAxiosError(err) && err.response?.status === 404) {
                    setForm(emptyForm)
                    return
                }
                setError('Nepavyko užkrauti profilio duomenų.')
            } finally {
                if (active) setLoading(false)
            }
        }

        void fetchProfile()

        return () => {
            active = false
        }
    }, [])

    const lastUpdatedText = useMemo(() => {
        if (!form.updatedAt) return 'Dar nebuvo atnaujinta'

        const value = new Date(form.updatedAt)
        if (Number.isNaN(value.getTime())) return 'Dar nebuvo atnaujinta'

        return value.toLocaleString('lt-LT', {
            dateStyle: 'medium',
            timeStyle: 'short',
        })
    }, [form.updatedAt])

    function toggleHabit(value: string) {
        setForm((current) => ({
            ...current,
            harmfulHabits: current.harmfulHabits.includes(value)
                ? current.harmfulHabits.filter((habit) => habit !== value)
                : [...current.harmfulHabits, value],
        }))
        setError('')
    }

    function addAllergy() {
        const value = allergyInput.trim()
        if (!value) return
        if (form.allergies.some((item) => item.toLowerCase() === value.toLowerCase())) {
            setAllergyInput('')
            return
        }

        setForm((current) => ({
            ...current,
            allergies: [...current.allergies, value],
        }))
        setAllergyInput('')
        setError('')
    }

    function removeAllergy(value: string) {
        setForm((current) => ({
            ...current,
            allergies: current.allergies.filter((item) => item !== value),
        }))
    }

    async function saveProfile() {
        const heightCm = Number(form.heightCm)
        const weightKg = Number(form.weightKg)
        const invalidHeight = !form.heightCm || Number.isNaN(heightCm) || heightCm < 50 || heightCm > 300
        const invalidWeight = !form.weightKg || Number.isNaN(weightKg) || weightKg < 20 || weightKg > 300

        setFieldError({
            heightCm: invalidHeight,
            weightKg: invalidWeight,
        })

        if (invalidHeight || invalidWeight) {
            setError('Ugis turi būti 50–300 cm, svoris – 20–300 kg.')
            return
        }

        setSaving(true)
        setError('')

        try {
            const response = await api.put<ProfileResponse>('/api/profile', {
                heightCm,
                weightKg,
                harmfulHabits: form.harmfulHabits,
                allergies: form.allergies,
            })

            setForm((current) => ({
                ...current,
                heightCm: response.data.heightCm != null ? String(response.data.heightCm) : current.heightCm,
                weightKg: response.data.weightKg != null ? String(response.data.weightKg) : current.weightKg,
                harmfulHabits: response.data.harmfulHabits ?? current.harmfulHabits,
                allergies: response.data.allergies ?? current.allergies,
                updatedAt: response.data.updatedAt ?? current.updatedAt,
            }))
        } catch (err) {
            if (axios.isAxiosError(err)) {
                const fieldErrors = err.response?.data?.fields
                if (fieldErrors && typeof fieldErrors === 'object') {
                    const messages = Object.values(fieldErrors).filter(Boolean)
                    setError(messages[0] as string || 'Nepavyko išsaugoti profilio.')
                    return
                }

                setError(err.response?.data?.message || 'Nepavyko išsaugoti profilio.')
                return
            }

            setError('Nepavyko susisiekti su serveriu.')
        } finally {
            setSaving(false)
        }
    }

    const displayName = user?.name || profile?.name || 'Naudotojas'

    return (
        <div className="profile-page">
            <div className="profile-header">
                <div>
                    <p className="eyebrow">Mano profilis</p>
                    <h1>{displayName}</h1>
                </div>
            </div>

            {loading ? <p className="muted">Kraunama...</p> : (
                <div className="profile-layout">
                    <section className="profile-card">
                        <div className="field-row">
                            <label className="field">
                                <span>Ūgis (cm)</span>
                                <input
                                    type="number"
                                    min={50}
                                    max={300}
                                    value={form.heightCm}
                                    className={fieldError.heightCm ? 'error-input' : ''}
                                    placeholder="Įrašykite ūgį (50-300cm)"
                                    onChange={(event) => {
                                        setForm((current) => ({ ...current, heightCm: event.target.value }))
                                        setFieldError((current) => ({ ...current, heightCm: false }))
                                        setError('')
                                    }}
                                />
                            </label>

                            <label className="field">
                                <span>Svoris (kg)</span>
                                <input
                                    type="number"
                                    min={20}
                                    max={300}
                                    step="0.1"
                                    value={form.weightKg}
                                    className={fieldError.weightKg ? 'error-input' : ''}
                                    placeholder="Įrašykite svorį (20-300 kg)"
                                    onChange={(event) => {
                                        setForm((current) => ({ ...current, weightKg: event.target.value }))
                                        setFieldError((current) => ({ ...current, weightKg: false }))
                                        setError('')
                                    }}
                                />
                            </label>
                        </div>

                        <div className="section-block">
                            <h2>Žalingi įpročiai</h2>
                            <div className="habit-list">
                                {HARMFUL_HABITS.map((habit) => (
                                    <label key={habit} className="checkbox-item">
                                        <input
                                            type="checkbox"
                                            checked={form.harmfulHabits.includes(habit)}
                                            onChange={() => toggleHabit(habit)}
                                        />
                                        <span>{habit}</span>
                                    </label>
                                ))}
                            </div>
                        </div>

                        <div className="section-block">
                            <h2>Alergijos</h2>
                            <div className="allergy-input-row">
                                <input
                                    type="text"
                                    value={allergyInput}
                                    onChange={(event) => setAllergyInput(event.target.value)}
                                    placeholder="Įrašykite alergiją"
                                    onKeyDown={(event) => {
                                        if (event.key === 'Enter') {
                                            event.preventDefault()
                                            addAllergy()
                                        }
                                    }}
                                />
                                <button type="button" className="btn secondary" onClick={addAllergy}>
                                    Pridėti
                                </button>
                            </div>

                            <div className="allergy-list">
                                {form.allergies.length === 0 ? (
                                    <p className="muted empty-state">Nėra įrašytų alergijų.</p>
                                ) : (
                                    form.allergies.map((allergy) => (
                                        <div key={allergy} className="allergy-tag">
                                            <span>{allergy}</span>
                                            <button type="button" onClick={() => removeAllergy(allergy)} aria-label={`Pašalinti ${allergy}`}>
                                                ×
                                            </button>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    </section>

                    <aside className="profile-sidebar">
                        <div className="save-card">
                            <span className="save-label">Paskutinį kartą atnaujinta</span>
                            <strong>{lastUpdatedText}</strong>
                            <button type="button" className="btn" onClick={saveProfile} disabled={saving}>
                                {saving ? 'Išsaugoma...' : 'Išsaugoti'}
                            </button>
                        </div>
                    </aside>
                </div>
            )}

            {error && <p className="error-banner" role="alert">{error}</p>}
        </div>
    )
}
