import { useEffect, useState } from 'react'
import { api } from '../api'
import SupplementEditor from '../components/SupplementEditor'
import type { Supplement, SupplementFormKind } from '../types'
import './CatalogPage.css'

const FORM_LABELS: Record<SupplementFormKind, string> = {
    CAPSULE: 'Kapsulė',
    TABLET: 'Tabletė',
    POWDER: 'Milteliai',
    LIQUID: 'Skystis',
}

/** Turinio administratoriaus papildų katalogo valdymo langas (US-10). */
export default function CatalogPage() {
    const [supplements, setSupplements] = useState<Supplement[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [notice, setNotice] = useState('')
    const [reload, setReload] = useState(0)
    const [editing, setEditing] = useState<'new' | number | null>(null)

    useEffect(() => {
        let active = true

        async function load() {
            try {
                const response = await api.get<Supplement[]>('/api/supplements')
                if (!active) return
                setSupplements(response.data)
                setError('')
            } catch {
                if (active) setError('Nepavyko užkrauti papildų katalogo.')
            } finally {
                if (active) setLoading(false)
            }
        }

        void load()

        return () => {
            active = false
        }
    }, [reload])

    const editingSupplement = typeof editing === 'number' ? supplements.find((s) => s.id === editing) ?? null : null

    async function remove(supplement: Supplement) {
        if (!window.confirm(`Ar tikrai norite pašalinti papildą „${supplement.name}“?`)) return
        setNotice('')
        try {
            const response = await api.delete(`/api/supplements/${supplement.id}`)
            setNotice(
                response.status === 200
                    ? `Papildas „${supplement.name}“ naudojamas vartojimo planuose, todėl pažymėtas kaip nebenaudojamas.`
                    : `Papildas „${supplement.name}“ pašalintas.`,
            )
            if (editing === supplement.id) setEditing(null)
            setReload((n) => n + 1)
        } catch {
            setError('Nepavyko pašalinti papildo.')
        }
    }

    return (
        <div className="catalog-page">
            <div className="catalog-header">
                <div>
                    <p className="eyebrow">Turinio administratorius</p>
                    <h1>Papildų katalogas</h1>
                </div>
                <button type="button" className="btn" onClick={() => setEditing('new')}>
                    Pridėti papildą
                </button>
            </div>

            {notice && <p className="notice">{notice}</p>}
            {error && <p className="error">{error}</p>}

            {(editing === 'new' || editingSupplement) && (
                <SupplementEditor
                    key={editingSupplement ? editingSupplement.id : 'new'}
                    supplement={editingSupplement}
                    allSupplements={supplements}
                    onSaved={(message) => {
                        setNotice(message)
                        setEditing(null)
                        setReload((n) => n + 1)
                    }}
                    onChanged={() => setReload((n) => n + 1)}
                    onCancel={() => setEditing(null)}
                />
            )}

            {loading ? (
                <p className="center">Kraunama…</p>
            ) : (
                <div className="catalog-table-wrap">
                    <table className="catalog-table">
                        <thead>
                        <tr>
                            <th>Pavadinimas</th>
                            <th>Veiklioji medžiaga</th>
                            <th>Forma</th>
                            <th>Dozė</th>
                            <th>Maks. paros dozė</th>
                            <th>Būsena</th>
                            <th>Sąveikos</th>
                            <th />
                        </tr>
                        </thead>
                        <tbody>
                        {supplements.map((s) => (
                            <tr key={s.id}>
                                <td>{s.name}</td>
                                <td>{s.activeSubstance}</td>
                                <td>{FORM_LABELS[s.form]}</td>
                                <td>
                                    {s.doseAmount} {s.doseUnit}
                                </td>
                                <td>
                                    {s.maxDailyDose} {s.doseUnit}
                                </td>
                                <td>
                                        <span className={s.status === 'ACTIVE' ? 'badge active' : 'badge inactive'}>
                                            {s.status === 'ACTIVE' ? 'Aktyvus' : 'Nebenaudojamas'}
                                        </span>
                                </td>
                                <td>{s.interactions.length}</td>
                                <td className="actions">
                                    <button
                                        type="button"
                                        className="btn secondary small"
                                        onClick={() => setEditing(s.id)}
                                    >
                                        Redaguoti
                                    </button>
                                    <button
                                        type="button"
                                        className="btn secondary small"
                                        onClick={() => void remove(s)}
                                    >
                                        Šalinti
                                    </button>
                                </td>
                            </tr>
                        ))}
                        {supplements.length === 0 && (
                            <tr>
                                <td colSpan={8} className="muted">
                                    Katalogas tuščias.
                                </td>
                            </tr>
                        )}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    )
}