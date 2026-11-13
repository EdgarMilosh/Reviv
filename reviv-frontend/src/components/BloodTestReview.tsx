import { useCallback, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { api } from '../api'
import './BloodTestReview.css'
import BloodTestAnalysis from './BloodTestAnalysis'

type Indicator = {
    id: number
    name: string
    rawValue: string | null
    unit: string | null
    referenceInterval: string | null
}

type Issue = {
    id: number
    indicatorId: number
    fieldName: string
    note: string | null
    resolved: boolean
}

type BloodTestDetail = {
    id: number
    fileName: string
    status: string
    laboratory: string | null
    testDate: string | null
    extractionError: string | null
    indicators: Indicator[]
    issues: Issue[]
}

const fieldLabels: Record<string, string> = {
    NAME: 'Pavadinimas',
    VALUE: 'Reikšmė',
    UNIT: 'Vienetas',
    REFERENCE: 'Referencinis intervalas'
}

const statusLabels: Record<string, string> = {
    UPLOADED: 'Įkelta',
    PROCESSING: 'DI apdoroja dokumentą',
    RETRY_QUEUED: 'Laukia pakartotinio ištraukimo',
    NEEDS_REVIEW: 'Laukia peržiūros',
    CONFIRMED: 'Patvirtinta',
    ERROR: 'Apdorojimo klaida'
}

function getErrorMessage(err: unknown, fallback: string): string {
    if (axios.isAxiosError(err)) {
        const data = err.response?.data as
            | { detail?: string; message?: string }
            | undefined
        return data?.detail || data?.message || fallback
    }
    return fallback
}

export default function BloodTestReview({ testId }: { testId: number }) {
    const [test, setTest] = useState<BloodTestDetail | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    // Checkbox valdo visa rodikli, o ne atskira pasirinkta lauka.
    // Reiksme true leidzia atidaryti pasirinkima ir dar neissaugo klaidos DB.
    const [openedIndicators, setOpenedIndicators] =
        useState<Record<number, boolean>>({})
    const [savingIndicatorId, setSavingIndicatorId] =
        useState<number | null>(null)
    const [isReextracting, setIsReextracting] = useState(false)
    const [isConfirming, setIsConfirming] = useState(false)
    const pollingStartedAt = useRef(Date.now())

    const loadTest = useCallback(async () => {
        try {
            const response = await api.get<BloodTestDetail>(
                `/api/tyrimai/${testId}`
            )
            setTest(response.data)
            setError('')
        } catch (err) {
            setError(getErrorMessage(err, 'Nepavyko gauti tyrimo duomenų.'))
        } finally {
            setLoading(false)
        }
    }, [testId])

    useEffect(() => {
        setTest(null)
        setLoading(true)
        setOpenedIndicators({})
        pollingStartedAt.current = Date.now()
        void loadTest()
    }, [loadTest])

    useEffect(() => {
        const status = test?.status
        if (!status || !['UPLOADED', 'PROCESSING', 'RETRY_QUEUED'].includes(status)) {
            return
        }

        const intervalId = window.setInterval(() => {
            if (Date.now() - pollingStartedAt.current > 90_000) {
                window.clearInterval(intervalId)
                return
            }
            void loadTest()
        }, 2000)

        return () => window.clearInterval(intervalId)
    }, [test?.status, loadTest])

    const unresolvedIssues = (test?.issues ?? []).filter(issue => !issue.resolved)
    const isBusy = savingIndicatorId !== null || isReextracting || isConfirming
    const canEdit = test?.status === 'NEEDS_REVIEW'

    // Pasirinktas checkbox be nurodyto klaidingo lauko yra nebaigtas zymejimas.
    const hasUnfinishedSelections = (test?.indicators ?? []).some(indicator =>
        openedIndicators[indicator.id] === true &&
        !unresolvedIssues.some(issue => issue.indicatorId === indicator.id)
    )

    useEffect(() => {
        // Po taisymo ar patvirtinimo nepaliekame atidarytu juodrasciu.
        if (test?.status === 'RETRY_QUEUED' ||
            test?.status === 'PROCESSING' ||
            test?.status === 'CONFIRMED') {
            setOpenedIndicators({})
        }
    }, [test?.status])

    async function markIssue(indicatorId: number, fieldName: string) {
        if (isBusy) return
        setOpenedIndicators(previous => ({ ...previous, [indicatorId]: true }))
        setSavingIndicatorId(indicatorId)
        setError('')
        try {
            const response = await api.post<BloodTestDetail>(
                `/api/tyrimai/${testId}/issues`,
                { indicatorId, fieldName, note: null }
            )
            setTest(response.data)
        } catch (err) {
            setError(getErrorMessage(err, 'Nepavyko pažymėti klaidos.'))
        } finally {
            setSavingIndicatorId(null)
        }
    }

    async function unmarkIssue(indicatorId: number, issueId: number) {
        if (isBusy) return
        setOpenedIndicators(previous => ({ ...previous, [indicatorId]: true }))
        setSavingIndicatorId(indicatorId)
        setError('')
        try {
            const response = await api.delete<BloodTestDetail>(
                `/api/tyrimai/${testId}/issues/${issueId}`
            )
            setTest(response.data)
            // Pasalinus paskutine klaidos zyma uzdarome pasirinkima ir varnele.
            const hasMoreIssues = response.data.issues.some(
                issue => !issue.resolved && issue.indicatorId === indicatorId
            )
            setOpenedIndicators(previous => ({
                ...previous,
                [indicatorId]: hasMoreIssues
            }))
        } catch (err) {
            setError(getErrorMessage(err, 'Nepavyko atžymėti klaidos.'))
        } finally {
            setSavingIndicatorId(null)
        }
    }


    async function toggleIndicator(
        indicatorId: number,
        checked: boolean,
        issues: Issue[]
    ) {
        if (!canEdit || isBusy) return

        // Uzdejimas tik atveria klaidos vietu pasirinkima.
        setOpenedIndicators(previous => ({ ...previous, [indicatorId]: checked }))
        if (checked || issues.length === 0) return

        // Nuemus varnele pasaliname VISAS neissprestas to rodiklio klaidas.
        // Naudojame jau egzistuojanti DELETE endpointa kiekvienai klaidai.
        setSavingIndicatorId(indicatorId)
        setError('')
        try {
            let latest: BloodTestDetail | null = null
            for (const issue of issues) {
                const response = await api.delete<BloodTestDetail>(
                    `/api/tyrimai/${testId}/issues/${issue.id}`
                )
                latest = response.data
            }
            if (latest) setTest(latest)
        } catch (err) {
            // Jei dalis istrynimu pavyko, parodome realia serverio busena.
            try {
                const current = await api.get<BloodTestDetail>(
                    `/api/tyrimai/${testId}`
                )
                setTest(current.data)
            } catch {
                // Paliekame anksciau zinomus duomenis.
            }
            setOpenedIndicators(previous => {
                const copy = { ...previous }
                delete copy[indicatorId]
                return copy
            })
            setError(getErrorMessage(
                err,
                'Nepavyko panaikinti visu sio rodiklio klaidu. Patikrinkite likusias zymas.'
            ))
        } finally {
            setSavingIndicatorId(null)
        }
    }

    async function reextract() {
        if (!canEdit || unresolvedIssues.length === 0 || hasUnfinishedSelections || isBusy) return
        if (!window.confirm(
            `Pakartotinai patikrinti ${unresolvedIssues.length} pažymėtus laukus? ` +
            'Bus vykdoma mokama OpenAI API užklausa.'
        )) return

        setIsReextracting(true)
        setError('')
        try {
            const response = await api.post<BloodTestDetail>(
                `/api/tyrimai/${testId}/reextract`
            )
            setTest(response.data)
            pollingStartedAt.current = Date.now()
        } catch (err) {
            setError(getErrorMessage(err, 'Nepavyko pradėti pakartotinio ištraukimo.'))
        } finally {
            setIsReextracting(false)
        }
    }

    async function confirmTest() {
        if (!canEdit || unresolvedIssues.length !== 0 || hasUnfinishedSelections || isBusy) return
        if (!window.confirm(
            'Ar patikrinote visus rodiklius pagal originalų PDF ir norite patvirtinti tyrimą?'
        )) return

        setIsConfirming(true)
        setError('')
        try {
            const response = await api.post<BloodTestDetail>(
                `/api/tyrimai/${testId}/confirm`
            )
            setTest(response.data)
        } catch (err) {
            setError(getErrorMessage(err, 'Nepavyko patvirtinti tyrimo.'))
        } finally {
            setIsConfirming(false)
        }
    }

    async function openOriginalPdf() {
        // Langą atidarome pačio paspaudimo metu, kad naršyklė jo neužblokuotų.
        const tab = window.open('', '_blank')
        if (!tab) {
            setError('Naršyklė užblokavo naują skirtuką. Leiskite iššokančius langus.')
            return
        }
        tab.opener = null
        setError('')
        try {
            const response = await api.get<Blob>(
                `/api/tyrimai/${testId}/pdf`,
                { responseType: 'blob' }
            )
            const url = URL.createObjectURL(
                new Blob([response.data], { type: 'application/pdf' })
            )
            tab.location.href = url
            window.setTimeout(() => URL.revokeObjectURL(url), 5 * 60 * 1000)
        } catch (err) {
            tab.close()
            setError(getErrorMessage(err, 'Nepavyko atidaryti PDF.'))
        }
    }

    if (loading) return <p>Įkeliami tyrimo duomenys...</p>

    return (
        <section className="blood-test-review">
            <div className="review-header">
                <h2>Tyrimo rodiklių peržiūra</h2>
                <button type="button" onClick={() => void loadTest()}>
                    Atnaujinti
                </button>
            </div>

            {error && <p role="alert" className="review-error">{error}</p>}

            {test && <>
                <div className="review-info">
                    <p><strong>Failas:</strong> {test.fileName}</p>
                    <p><strong>Būsena:</strong> {statusLabels[test.status] ?? test.status}</p>
                    <p><strong>Laboratorija:</strong> {test.laboratory ?? 'Nenurodyta'}</p>
                    <p><strong>Tyrimo data:</strong> {test.testDate ?? 'Nenurodyta'}</p>
                    <p><strong>Rodiklių skaičius:</strong> {test.indicators.length}</p>
                </div>

                <button
                    type="button"
                    className="review-pdf-button"
                    onClick={() => void openOriginalPdf()}
                >
                    Peržiūrėti originalų PDF
                </button>

                {test.extractionError && (
                    <p className="review-error">{test.extractionError}</p>
                )}

                {test.status !== 'CONFIRMED' && test.indicators.length > 0 && (
                    <div className="review-table-container">
                        <table className="review-table">
                            <thead><tr>
                                <th>Rodiklis</th>
                                <th>Reikšmė</th>
                                <th>Vienetas</th>
                                <th>Referencinis intervalas</th>
                                <th>Klaida?</th>
                                <th>Kas neteisinga?</th>
                            </tr></thead>
                            <tbody>
                            {test.indicators.map(indicator => {
                                const issues = unresolvedIssues.filter(
                                    issue => issue.indicatorId === indicator.id
                                )
                                const checked = openedIndicators[indicator.id]
                                    ?? issues.length > 0
                                const availableFields = Object.entries(fieldLabels).filter(
                                    ([fieldName]) => !issues.some(
                                        issue => issue.fieldName === fieldName
                                    )
                                )
                                const savingThis = savingIndicatorId === indicator.id

                                return (
                                    <tr
                                        key={indicator.id}
                                        className={issues.length ? 'review-marked-row' : ''}
                                    >
                                        <td>{indicator.name}</td>
                                        <td>{indicator.rawValue ?? '–'}</td>
                                        <td>{indicator.unit ?? '–'}</td>
                                        <td>{indicator.referenceInterval ?? '–'}</td>
                                        <td className="review-checkbox-cell">
                                            <input
                                                type="checkbox"
                                                checked={checked}
                                                disabled={!canEdit || isBusy}
                                                onChange={event => {
                                                    void toggleIndicator(
                                                        indicator.id,
                                                        event.target.checked,
                                                        issues
                                                    )
                                                }}
                                                aria-label={`${indicator.name}: klaidingai atpažinta`}
                                                aria-controls={`review-fields-${indicator.id}`}
                                            />
                                        </td>
                                        <td>
                                            <div
                                                className="review-field-editor"
                                                id={`review-fields-${indicator.id}`}
                                            >
                                                {checked && (
                                                    <>
                                                        {issues.length === 0 && (
                                                            <span className="review-selection-hint">
                                                                    Pasirinkite klaidos vietą
                                                                </span>
                                                        )}
                                                        <div className="review-issue-tags">
                                                            {issues.map(issue => (
                                                                <span
                                                                    className="review-issue-tag"
                                                                    key={issue.id}
                                                                >
                                                                        {fieldLabels[issue.fieldName] ?? issue.fieldName}
                                                                    {canEdit && (
                                                                        <button
                                                                            type="button"
                                                                            className="review-issue-remove"
                                                                            onClick={() => void unmarkIssue(
                                                                                indicator.id,
                                                                                issue.id
                                                                            )}
                                                                            disabled={isBusy}
                                                                            aria-label={`Pašalinti klaidą: ${indicator.name}, ${fieldLabels[issue.fieldName] ?? issue.fieldName}`}
                                                                            title="Atšaukti šią klaidą"
                                                                        >
                                                                            ×
                                                                        </button>
                                                                    )}
                                                                    </span>
                                                            ))}
                                                        </div>
                                                        {availableFields.length > 0 && canEdit && (
                                                            <select
                                                                className="review-field-select"
                                                                aria-label={`Kas neteisinga: ${indicator.name}`}
                                                                value=""
                                                                disabled={isBusy}
                                                                onChange={event => {
                                                                    if (event.target.value) {
                                                                        void markIssue(
                                                                            indicator.id,
                                                                            event.target.value
                                                                        )
                                                                    }
                                                                }}
                                                            >
                                                                <option value="" disabled>
                                                                    + Pasirinkite, kas neteisinga
                                                                </option>
                                                                {availableFields.map(([value, label]) => (
                                                                    <option key={value} value={value}>
                                                                        {label}
                                                                    </option>
                                                                ))}
                                                            </select>
                                                        )}
                                                        {savingThis && (
                                                            <span className="review-saving">
                                                                    Išsaugoma...
                                                                </span>
                                                        )}
                                                    </>
                                                )}
                                                {!checked && (
                                                    <span className="review-no-errors">–</span>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                )
                            })}
                            </tbody>
                        </table>
                    </div>
                )}

                {canEdit && <>
                    <p className="review-notice">
                        Prieš patvirtindami sulyginkite ištrauktus rodiklius su originaliu PDF.
                        Už normos ribų esanti reikšmė savaime nėra DI atpažinimo klaida.
                    </p>
                    <div className="review-actions">
                        <div className="review-issue-summary">
                            <p className="review-issue-count">
                                Pažymėtų klaidingų laukų: <strong>{unresolvedIssues.length}</strong>
                            </p>
                            {hasUnfinishedSelections && (
                                <p className="review-selection-warning">
                                    Pasirinkite klaidos vietą arba nuimkite varnelę.
                                </p>
                            )}
                        </div>
                        <div className="review-action-buttons">
                            <button
                                type="button"
                                className="review-reextract-button"
                                disabled={unresolvedIssues.length === 0 || hasUnfinishedSelections || isBusy}
                                onClick={() => void reextract()}
                            >
                                {isReextracting ? 'Siunčiama...' : 'Ištraukti iš naujo'}
                            </button>
                            <button
                                type="button"
                                className="review-confirm-button"
                                disabled={unresolvedIssues.length > 0 || hasUnfinishedSelections || isBusy}
                                onClick={() => void confirmTest()}
                            >
                                {isConfirming ? 'Patvirtinama...' : 'Patvirtinti tyrimą'}
                            </button>
                        </div>
                    </div>
                </>}

                {['UPLOADED', 'PROCESSING', 'RETRY_QUEUED'].includes(test.status) && (
                    <p className="review-processing" role="status">
                        Vyksta tyrimo apdorojimas. Duomenys atnaujinami automatiškai.
                    </p>
                )}

                {test.status === 'CONFIRMED' && (
                    <>
                        <p className="review-confirmed" role="status">
                            Tyrimas patvirtintas. Rodikliai išsaugoti
                            ir paruošti tolesnei analizei.
                        </p>

                        <BloodTestAnalysis testId={testId} />
                    </>
                )}
            </>}
        </section>
    )
}
