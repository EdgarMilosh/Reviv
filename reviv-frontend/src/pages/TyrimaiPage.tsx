import { useEffect, useMemo, useState } from 'react'
import axios from 'axios'
import { Link } from 'react-router-dom'
import { api } from '../api'
import './TyrimaiPage.css'

type BloodTestItem = {
    id: number
    fileName: string
    laboratory: string | null
    status: string
    uploadedAt: string
}

type StatusFilter = 'all' | 'needs-review' | 'confirmed' | 'processing' | 'error'

const statusLabels: Record<string, string> = {
    UPLOADED: 'Įkelta',
    PROCESSING: 'Apdorojama',
    RETRY_QUEUED: 'Laukia pakartotinio',
    NEEDS_REVIEW: 'Laukia peržiūros',
    CONFIRMED: 'Patvirtinta',
    ERROR: 'Klaida',
}

function dateLabel(value: string | null | undefined) {
    if (!value) return 'Nenurodyta'
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('lt-LT')
}

function getErrorMessage(error: unknown, fallback: string) {
    if (axios.isAxiosError(error)) {
        const data = error.response?.data as { detail?: string; message?: string } | undefined
        return data?.detail ?? data?.message ?? fallback
    }
    return fallback
}

export default function TyrimaiPage() {
    const [tests, setTests] = useState<BloodTestItem[]>([])
    const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')
    const [search, setSearch] = useState('')
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [deletingId, setDeletingId] = useState<number | null>(null)
    const [reloadKey, setReloadKey] = useState(0)

    useEffect(() => {
        let cancelled = false

        api.get<BloodTestItem[]>('/api/tyrimai', {
            params: search.trim() ? { filter: search.trim() } : undefined,
        })
            .then((response) => {
                if (!cancelled) {
                    setTests(response.data)
                    setError('')
                }
            })
            .catch((err) => {
                if (!cancelled) {
                    setError(getErrorMessage(err, 'Nepavyko gauti tyrimų sąrašo.'))
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoading(false)
                }
            })

        return () => { cancelled = true }
    }, [search, reloadKey])

    const displayed = useMemo(() => {
        return tests.filter((test) => {
            if (statusFilter === 'all') return true
            if (statusFilter === 'needs-review') return test.status === 'NEEDS_REVIEW'
            if (statusFilter === 'confirmed') return test.status === 'CONFIRMED'
            if (statusFilter === 'processing') return ['UPLOADED', 'PROCESSING', 'RETRY_QUEUED'].includes(test.status)
            if (statusFilter === 'error') return test.status === 'ERROR'
            return true
        })
    }, [tests, statusFilter])

    async function openPdf(testId: number) {
        try {
            const response = await api.get(`/api/tyrimai/${testId}/pdf`, {
                responseType: 'blob',
            })
            const blob = new Blob([response.data], { type: 'application/pdf' })
            const url = URL.createObjectURL(blob)
            window.open(url, '_blank', 'noopener,noreferrer')
        } catch (err) {
            window.alert(getErrorMessage(err, 'Nepavyko atidaryti PDF.'))
        }
    }

    async function deleteTest(testId: number) {
        const confirmed = window.confirm('Ar tikrai norite ištrinti šį tyrimą?')
        if (!confirmed) return

        setDeletingId(testId)
        try {
            await api.delete(`/api/tyrimai/${testId}`)
            setTests((current) => current.filter((test) => test.id !== testId))
            setError('')
        } catch (err) {
            setError(getErrorMessage(err, 'Nepavyko ištrinti tyrimo.'))
        } finally {
            setDeletingId(null)
        }
    }

    return (
        <section className="tests-page">
            <div className="tests-topbar">
                <div>
                    <h1>Tyrimai</h1>
                    <p>Visų jūsų įkeltų tyrimų sąrašas vienoje vietoje. Peržiūrėkite rezultatus, PDF ar ištrinkite nebereikalingus dokumentus.</p>
                </div>
                <Link className="tests-new-link" to="/upload">+ Įkelti naują tyrimą</Link>
            </div>

            {!loading && !error && tests.length > 0 && (
                <div className="tests-stats" aria-label="Tyrimų suvestinė">
                    <div><strong>{tests.length}</strong><span>Visi tyrimai</span></div>
                    <div><strong>{tests.filter((t) => t.status === 'NEEDS_REVIEW').length}</strong><span>Laukia peržiūros</span></div>
                    <div><strong>{tests.filter((t) => t.status === 'CONFIRMED').length}</strong><span>Patvirtinti</span></div>
                </div>
            )}

            {loading && <p role="status" className="tests-message">Įkeliami tyrimai...</p>}

            {error && (
                <div className="tests-message tests-error" role="alert">
                    <p>{error}</p>
                    <button type="button" onClick={() => { setLoading(true); setReloadKey((key) => key + 1) }}>Bandyti dar kartą</button>
                </div>
            )}

            {!loading && !error && tests.length === 0 && (
                <div className="tests-message tests-empty">
                    <h2>Tyrimų dar nėra</h2>
                    <p>Įkelkite pirmąjį tyrimo PDF ir jis atsiras šiame sąraše.</p>
                    <Link to="/upload">Atidaryti dokumentų įkėlimą</Link>
                </div>
            )}

            {!loading && !error && tests.length > 0 && (
                <>
                    <div className="tests-controls">
                        <div className="tests-filter" role="group" aria-label="Filtruoti tyrimus">
                            <button type="button" aria-pressed={statusFilter === 'all'} className={statusFilter === 'all' ? 'selected' : ''} onClick={() => setStatusFilter('all')}>Visi</button>
                            <button type="button" aria-pressed={statusFilter === 'needs-review'} className={statusFilter === 'needs-review' ? 'selected' : ''} onClick={() => setStatusFilter('needs-review')}>Laukia peržiūros</button>
                            <button type="button" aria-pressed={statusFilter === 'confirmed'} className={statusFilter === 'confirmed' ? 'selected' : ''} onClick={() => setStatusFilter('confirmed')}>Patvirtinti</button>
                            <button type="button" aria-pressed={statusFilter === 'processing'} className={statusFilter === 'processing' ? 'selected' : ''} onClick={() => setStatusFilter('processing')}>Vykdoma</button>
                            <button type="button" aria-pressed={statusFilter === 'error'} className={statusFilter === 'error' ? 'selected' : ''} onClick={() => setStatusFilter('error')}>Klaidos</button>
                        </div>
                        <label className="tests-search">
                            <span>Ieškoti tyrimo</span>
                            <input type="search" value={search} placeholder="Pavadinimas arba laboratorija" onChange={(event) => setSearch(event.target.value)} />
                        </label>
                    </div>

                    <div className="tests-grid">
                        {displayed.map((test) => (
                            <article className="tests-card" key={test.id}>
                                <div className="tests-card-header">
                                    <div>
                                        <h2>{test.fileName}</h2>
                                        <span className="tests-card-date">Įkeltas {dateLabel(test.uploadedAt)}</span>
                                    </div>
                                    <span className={`tests-status ${test.status.toLowerCase()}`}>{statusLabels[test.status] ?? test.status}</span>
                                </div>

                                <dl className="tests-meta">
                                    <div><dt>Laboratorija</dt><dd>{test.laboratory ?? 'Nenurodyta'}</dd></div>
                                    <div><dt>Būsena</dt><dd>{statusLabels[test.status] ?? test.status}</dd></div>
                                </dl>

                                <div className="tests-card-actions">
                                    <Link className="tests-open" to={`/upload?testId=${test.id}`}>Atidaryti</Link>
                                    <button type="button" className="tests-secondary" onClick={() => void openPdf(test.id)}>Peržiūrėti PDF</button>
                                    <button type="button" className="tests-danger" disabled={deletingId === test.id} onClick={() => void deleteTest(test.id)}>
                                        {deletingId === test.id ? 'Trinamas...' : 'Ištrinti'}
                                    </button>
                                </div>
                            </article>
                        ))}
                    </div>

                    {displayed.length === 0 && <p className="tests-message">Nerasta tyrimų pagal pasirinktą filtrą.</p>}
                </>
            )}
        </section>
    )
}
