import { useCallback, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { api } from '../api'
import './BloodTestReview.css'

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
    const [selectedFields, setSelectedFields] =
        useState<Record<number, string>>({})
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

    async function markIssue(indicatorId: number, fieldName: string) {
        if (isBusy) return
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
        setSavingIndicatorId(indicatorId)
        setError('')
        try {
            const response = await api.delete<BloodTestDetail>(
                `/api/tyrimai/${testId}/issues/${issueId}`
            )
            setTest(response.data)
        } catch (err) {
            setError(getErrorMessage(err, 'Nepavyko atžymėti klaidos.'))
        } finally {
            setSavingIndicatorId(null)
        }
    }

    async function reextract() {
        if (!canEdit || unresolvedIssues.length === 0 || isBusy) return
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
        if (!canEdit || unresolvedIssues.length !== 0 || isBusy) return
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

                {test.indicators.length > 0 && (
                    <div className="review-table-container">
                        <table className="review-table">
                            <thead><tr>
                                <th>Rodiklis</th>
                                <th>Reikšmė</th>
                                <th>Vienetas</th>
                                <th>Referencinis intervalas</th>
                                <th>Klaidingai atpažinta</th>
                                <th>Klaidos vieta</th>
                            </tr></thead>
                            <tbody>
                            {test.indicators.map(indicator => {
                                const issues = unresolvedIssues.filter(
                                    issue => issue.indicatorId === indicator.id
                                )
                                const selectedField = selectedFields[indicator.id]
                                    ?? issues[0]?.fieldName
                                    ?? 'VALUE'
                                const currentIssue = issues.find(
                                    issue => issue.fieldName === selectedField
                                )

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
                                                checked={Boolean(currentIssue)}
                                                disabled={!canEdit || isBusy}
                                                onChange={() => {
                                                    if (currentIssue) {
                                                        void unmarkIssue(indicator.id, currentIssue.id)
                                                    } else {
                                                        void markIssue(indicator.id, selectedField)
                                                    }
                                                }}
                                                aria-label={`${indicator.name}: ${fieldLabels[selectedField]}`}
                                            />
                                        </td>
                                        <td>
                                            <select
                                                className="review-field-select"
                                                aria-label={`Klaidos vieta: ${indicator.name}`}
                                                value={selectedField}
                                                disabled={!canEdit || isBusy}
                                                onChange={event => {
                                                    setSelectedFields(previous => ({
                                                        ...previous,
                                                        [indicator.id]: event.target.value
                                                    }))
                                                }}
                                            >
                                                {Object.entries(fieldLabels).map(([value, label]) => (
                                                    <option value={value} key={value}>{label}</option>
                                                ))}
                                            </select>
                                            {issues.length > 0 && (
                                                <div className="review-marked-label">
                                                    Pažymėta: {issues.map(issue =>
                                                    fieldLabels[issue.fieldName] ?? issue.fieldName
                                                ).join(', ')}
                                                </div>
                                            )}
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
                        <p className="review-issue-count">
                            Pažymėtų klaidingų laukų: <strong>{unresolvedIssues.length}</strong>
                        </p>
                        <div className="review-action-buttons">
                            <button
                                type="button"
                                className="review-reextract-button"
                                disabled={unresolvedIssues.length === 0 || isBusy}
                                onClick={() => void reextract()}
                            >
                                {isReextracting ? 'Siunčiama...' : 'Ištraukti iš naujo'}
                            </button>
                            <button
                                type="button"
                                className="review-confirm-button"
                                disabled={unresolvedIssues.length > 0 || isBusy}
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
                    <p className="review-confirmed" role="status">
                        Tyrimas patvirtintas. Rodikliai išsaugoti ir paruošti tolesnei analizei.
                    </p>
                )}
            </>}
        </section>
    )
}
