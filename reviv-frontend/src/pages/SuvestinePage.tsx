import { useMemo, useState } from 'react'
import axios from 'axios'
import { api } from '../api'
import './SuvestinePage.css'

type SummaryPeriod = 'THREE_MONTHS' | 'SIX_MONTHS' | 'ONE_YEAR' | 'ALL'

type SectionKey = 'TESTS' | 'ANALYSES' | 'SUPPLEMENT_PLAN' | 'CONSUMPTION_HISTORY'

const PERIOD_OPTIONS: { value: SummaryPeriod; label: string }[] = [
    { value: 'THREE_MONTHS', label: '3 mėn.' },
    { value: 'SIX_MONTHS', label: '6 mėn.' },
    { value: 'ONE_YEAR', label: '1 metai' },
    { value: 'ALL', label: 'Visi duomenys' },
]

const SECTION_OPTIONS: { key: SectionKey; label: string; description: string }[] = [
    { key: 'TESTS', label: 'Tyrimai', description: 'Reikšmės, vienetai, referenciniai intervalai' },
    { key: 'ANALYSES', label: 'Analizė', description: 'DI sugeneruoti paaiškinimai' },
    { key: 'SUPPLEMENT_PLAN', label: 'Vartojimo planas', description: 'Papildų dozės ir ciklai' },
    { key: 'CONSUMPTION_HISTORY', label: 'Vartojimo istorija', description: 'Nuoseklumo statistika pasirinktu laikotarpiu' },
]

const sectionMap: Record<SectionKey, string> = {
    TESTS: 'Tyrimai',
    ANALYSES: 'Analizė',
    SUPPLEMENT_PLAN: 'Vartojimo planas',
    CONSUMPTION_HISTORY: 'Vartojimo istorija',
}

function getErrorMessage(error: unknown, fallback: string) {
    if (axios.isAxiosError(error)) {
        const data = error.response?.data as { detail?: string; message?: string } | undefined
        return data?.detail ?? data?.message ?? fallback
    }
    return fallback
}

function parseFilename(headerValue: string | undefined) {
    if (!headerValue) return undefined
    const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(headerValue)
    if (!match) return undefined
    return decodeURIComponent(match[1].replace(/\+/g, ' '))
}

export default function SuvestinePage() {
    const [period, setPeriod] = useState<SummaryPeriod>('ALL')
    const [sections, setSections] = useState<Record<SectionKey, boolean>>({
        TESTS: true,
        ANALYSES: true,
        SUPPLEMENT_PLAN: true,
        CONSUMPTION_HISTORY: true,
    })
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')

    const selectedSections = useMemo(
        () => (Object.keys(sections) as SectionKey[]).filter((key) => sections[key]),
        [sections]
    )

    function toggleSection(key: SectionKey) {
        setSections(current => ({ ...current, [key]: !current[key] }))
        setError('')
    }

    async function exportSummary() {
        if (selectedSections.length === 0) {
            setError('Pasirinkite bent vieną suvestinės dalį.')
            return
        }

        setLoading(true)
        setError('')

        try {
            const response = await api.post<Blob>(
                '/api/suvestine',
                { period, sections: selectedSections },
                { responseType: 'blob' }
            )

            const blob = new Blob([response.data], { type: 'application/pdf' })
            const url = URL.createObjectURL(blob)
            const filename = parseFilename(response.headers['content-disposition'])
                ?? `Reviv_suvestine_${new Date().toISOString().slice(0, 10)}.pdf`

            const link = document.createElement('a')
            link.href = url
            link.download = filename
            link.style.display = 'none'
            document.body.appendChild(link)
            link.click()
            link.remove()
            window.setTimeout(() => URL.revokeObjectURL(url), 10_000)
        } catch (err) {
            setError(getErrorMessage(err, 'Nepavyko sugeneruoti PDF suvestinės.'))
        } finally {
            setLoading(false)
        }
    }

    return (
        <section className="summary-page">
            <div className="summary-header">
                <div>
                    <p className="eyebrow">Gydytojo suvestinė</p>
                    <h1>Eksportuoti PDF suvestinę</h1>
                </div>
            </div>

            <div className="summary-layout">
                <div className="summary-card summary-options">
                    <h2>Pasirinkti laikotarpį</h2>
                    <div className="period-list" role="radiogroup" aria-label="Laikotarpis suvestinei">
                        {PERIOD_OPTIONS.map(option => (
                            <label key={option.value} className={`choice-option ${period === option.value ? 'selected' : ''}`}>
                                <input
                                    type="radio"
                                    name="summary-period"
                                    checked={period === option.value}
                                    onChange={() => setPeriod(option.value)}
                                />
                                <span>{option.label}</span>
                            </label>
                        ))}
                    </div>

                    <h2>Pasirinkti duomenis</h2>
                    <div className="section-list">
                        {SECTION_OPTIONS.map(option => (
                            <label key={option.key} className={`choice-option ${sections[option.key] ? 'selected' : ''}`}>
                                <input
                                    type="checkbox"
                                    checked={sections[option.key]}
                                    onChange={() => toggleSection(option.key)}
                                />
                                <span>
                                    <strong>{option.label}</strong>
                                    <small>{option.description}</small>
                                </span>
                            </label>
                        ))}
                    </div>

                    <div className="summary-actions">
                        <button type="button" className="summary-export-btn" onClick={() => void exportSummary()} disabled={loading}>
                            {loading ? 'Generuojama PDF...' : 'Eksportuoti PDF'}
                        </button>
                    </div>

                    {error && (
                        <p className="summary-error" role="alert">{error}</p>
                    )}
                </div>

                <div className="summary-card summary-preview">
                    <h2>Suvestinės turinys</h2>
                    <ul>
                        <li>Naudotojo profilis: amžius, ūgis, svoris, alergijos, žalingi įpročiai.</li>
                        <li>Tyrimų rodikliai: reikšmės, vienetai ir referenciniai intervalai.</li>
                        <li>DI sugeneruoti paaiškinimai pažymėti kaip DI turinys.</li>
                        <li>Papildų vartojimo planas ir istorija pasirinktu laikotarpiu.</li>
                    </ul>

                    <div className="summary-selected">
                        <span>Pasirinkta:</span>
                        <div className="summary-tags">
                            {selectedSections.length > 0 ? (
                                selectedSections.map(section => (
                                    <span key={section} className="summary-tag">{sectionMap[section as SectionKey]}</span>
                                ))
                            ) : (
                                <span className="summary-tag empty">Nėra pasirinkta</span>
                            )}
                        </div>
                    </div>

                    <div className="summary-note">
                        <strong>Pastaba:</strong> sugeneruota suvestinė nėra diagnozė ir skirta informaciniam gydytojo peržiūros tikslui.
                    </div>
                </div>
            </div>
        </section>
    )
}
