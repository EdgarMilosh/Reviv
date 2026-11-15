import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import axios from 'axios'
import { api } from '../api'
import './PlansPage.css'

type PlanSummary = {
    id: number
    bloodTestId: number
    bloodTestFileName: string
    laboratory: string | null
    bloodTestDate: string | null
    status: 'ACTIVE' | 'DRAFT'
    createdAt: string
    updatedAt: string
    startDate: string | null
    endDate: string | null
    supplements: string[]
}

type Filter = 'all' | 'active' | 'draft'

function dateLabel(value: string | null | undefined) {
    if (!value) return 'Nenurodyta'
    const date = value.length === 10 ? new Date(`${value}T12:00:00`) : new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('lt-LT')
}

function errorMessage(error: unknown) {
    if (axios.isAxiosError(error)) {
        const data = error.response?.data as { message?: string; detail?: string } | undefined
        return data?.detail ?? data?.message ?? 'Nepavyko gauti išsaugotų planų.'
    }
    return 'Nepavyko susisiekti su serveriu.'
}

export default function PlansPage() {
    const [plans, setPlans] = useState<PlanSummary[]>([])
    const [filter, setFilter] = useState<Filter>('all')
    const [search, setSearch] = useState('')
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [reloadKey, setReloadKey] = useState(0)

    useEffect(() => {
        let cancelled = false
        api.get<PlanSummary[]>('/api/planai').then(response => {
            if (!cancelled) {
                setPlans(response.data)
                setError('')
            }
        }).catch(err => {
            if (!cancelled) setError(errorMessage(err))
        }).finally(() => {
            if (!cancelled) setLoading(false)
        })
        return () => { cancelled = true }
    }, [reloadKey])

    const displayed = useMemo(() => {
        const query = search.trim().toLocaleLowerCase('lt-LT')
        return plans.filter(plan => {
            if (filter === 'active' && plan.status !== 'ACTIVE') return false
            if (filter === 'draft' && plan.status !== 'DRAFT') return false
            if (!query) return true
            return [String(plan.id), String(plan.bloodTestId), plan.bloodTestFileName,
                plan.laboratory ?? '', ...plan.supplements]
                .some(item => item.toLocaleLowerCase('lt-LT').includes(query))
        })
    }, [plans, filter, search])

    return (
        <section className="plans-library">
            <div className="plans-topbar">
                <div>
                    <h1>Mano vartojimo planai</h1>
                    <p>Visi išsaugoti planai vienoje vietoje. Atidarykite planą jo peržiūrai ar koregavimui.</p>
                </div>
                <Link className="plans-new-link" to="/upload">+ Įkelti naują tyrimą</Link>
            </div>

            {!loading && !error && plans.length > 0 && (
                <div className="plans-stats" aria-label="Planų suvestinė">
                    <div><strong>{plans.length}</strong><span>Visi planai</span></div>
                    <div><strong>{plans.filter(p => p.status === 'ACTIVE').length}</strong><span>Patvirtinti</span></div>
                    <div><strong>{plans.filter(p => p.status === 'DRAFT').length}</strong><span>Juodraščiai</span></div>
                </div>
            )}

            {loading && <p role="status" className="plans-message">Įkeliami vartojimo planai...</p>}
            {error && (
                <div className="plans-message plans-error" role="alert">
                    <p>{error}</p>
                    <button type="button" onClick={() => { setLoading(true); setReloadKey(k => k + 1) }}>Bandyti dar kartą</button>
                </div>
            )}
            {!loading && !error && plans.length === 0 && (
                <div className="plans-message plans-empty">
                    <h2>Vartojimo planų dar nėra</h2>
                    <p>Patvirtinkite US-9 papildų rekomendacijas ir pasirinkite „Gauti vartojimo planą“. Tuomet jis atsiras šiame sąraše.</p>
                    <Link to="/upload">Atidaryti tyrimų įkėlimą</Link>
                </div>
            )}

            {!loading && !error && plans.length > 0 && <>
                <div className="plans-controls">
                    <div className="plans-filter" role="group" aria-label="Filtruoti planus">
                        <button type="button" aria-pressed={filter === 'all'} className={filter === 'all' ? 'selected' : ''} onClick={() => setFilter('all')}>Visi</button>
                        <button type="button" aria-pressed={filter === 'active'} className={filter === 'active' ? 'selected' : ''} onClick={() => setFilter('active')}>Patvirtinti</button>
                        <button type="button" aria-pressed={filter === 'draft'} className={filter === 'draft' ? 'selected' : ''} onClick={() => setFilter('draft')}>Juodraščiai</button>
                    </div>
                    <label className="plans-search">
                        <span>Ieškoti plano</span>
                        <input type="search" value={search} placeholder="Papildas, tyrimas, laboratorija..." onChange={e => setSearch(e.target.value)} />
                    </label>
                </div>

                <div className="plans-grid">
                    {displayed.map(plan => (
                        <article className="plans-card" key={plan.id}>
                            <div className="plans-card-header">
                                <div>
                                    <h2>Vartojimo planas #{plan.id}</h2>
                                    <span className="plans-card-date">Sukurtas {dateLabel(plan.createdAt)}</span>
                                </div>
                                <span className={`plans-status ${plan.status === 'ACTIVE' ? 'is-active' : 'is-draft'}`}>
                                    {plan.status === 'ACTIVE' ? 'Patvirtintas' : 'Juodraštis'}
                                </span>
                            </div>
                            <dl className="plans-card-meta">
                                <div><dt>Tyrimas</dt><dd title={plan.bloodTestFileName}>{plan.bloodTestFileName}</dd></div>
                                {plan.bloodTestDate && <div><dt>Tyrimo data</dt><dd>{dateLabel(plan.bloodTestDate)}</dd></div>}
                                {plan.laboratory && <div><dt>Laboratorija</dt><dd>{plan.laboratory}</dd></div>}
                                <div><dt>Vartojimo laikotarpis</dt><dd>{plan.startDate && plan.endDate ? `${dateLabel(plan.startDate)} – ${dateLabel(plan.endDate)}` : 'Nenustatytas'}</dd></div>
                            </dl>
                            <div className="plans-supplements">
                                <span className="plans-supplements-title">Papildai ({plan.supplements.length})</span>
                                <div>{plan.supplements.map(name => <span className="plans-supplement-chip" key={name}>{name}</span>)}</div>
                            </div>
                            <div className="plans-card-actions">
                                <Link className="plans-open" to={`/plans/${plan.id}`}>Atidaryti planą</Link>
                                <Link className="plans-source" to={`/upload?testId=${plan.bloodTestId}`}>Peržiūrėti tyrimą</Link>
                            </div>
                        </article>
                    ))}
                </div>
                {displayed.length === 0 && <p className="plans-message">Nerasta planų pagal pasirinktą filtrą ar paiešką.</p>}
            </>}
        </section>
    )
}
