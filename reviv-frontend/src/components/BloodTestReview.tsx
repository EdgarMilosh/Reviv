import { useCallback, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { api } from '../api'
import './BloodTestReview.css'
import BloodTestResults, { type BloodIndicator } from './BloodTestResults'
import BloodTestAnalysis from './BloodTestAnalysis'
import RecommendationPreview, {
    type RecommendationPreviewResponse,
    type LatestRecommendationsResponse,
    type SavedRecommendationItem,
} from './RecommendationPreview'
import ConsumptionPlan, { type ConsumptionPlanData } from './ConsumptionPlan'

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
    indicators: BloodIndicator[]
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

// Pakartotinai atidarant langą užkrauname paskutinį išsaugotą generavimą.
// Latest API saugo sprendimus, tačiau dar negrąžina papildo formos ir veikliosios medžiagos;
// jų nerodome, kol šie laukai nebus pridėti prie išsaugotos informacijos API.
function previewFromLatest(
    latest: LatestRecommendationsResponse,
    indicators: BloodIndicator[],
    current?: RecommendationPreviewResponse | null
): RecommendationPreviewResponse {
    const nameByIndicator = new Map(indicators.map(indicator => [indicator.id, indicator.name]))

    return {
        bloodTestId: latest.bloodTestId,
        criticalDetected: false,
        criticalityNotAssessed: latest.criticalityNotAssessed,
        normalCount: indicators.filter(indicator => indicator.indicatorStatus === 'WITHIN_RANGE').length,
        undeterminedCount: indicators.filter(indicator => indicator.indicatorStatus === 'UNDETERMINED').length,
        canAccept: false, // Ne medicininis patvirtinimas; vartotojo pasirinkimą leidžia decision API.
        recommendations: latest.items
            .filter(item => item.recommendationStatus === 'SUGGESTED')
            .map(item => {
                const matching = current?.recommendations.find(previous =>
                    previous.indicatorId === item.indicatorId
                    && previous.supplementId === item.supplementId
                    && previous.reason === item.reason
                )
                return {
                    indicatorId: item.indicatorId,
                    indicatorName: nameByIndicator.get(item.indicatorId) ?? `Rodiklis #${item.indicatorId}`,
                    supplementId: item.supplementId ?? 0,
                    supplementName: item.supplementName ?? 'Papildas nenurodytas',
                    activeSubstance: matching?.activeSubstance ?? '',
                    form: matching?.form ?? '',
                    proposedDailyDose: item.proposedDailyDose,
                    doseUnit: item.doseUnit,
                    durationDays: item.durationDays,
                    reason: item.reason,
                    warnings: item.warnings,
                }
            }),
        notRecommended: latest.items
            .filter(item => item.recommendationStatus !== 'SUGGESTED')
            .map(item => ({
                indicatorId: item.indicatorId,
                indicatorName: nameByIndicator.get(item.indicatorId) ?? `Rodiklis #${item.indicatorId}`,
                status: item.recommendationStatus as 'NOT_RECOMMENDED' | 'NEEDS_REVIEW',
                reason: item.reason,
                warnings: item.warnings,
            })),
        notices: [
            ...(latest.criticalityNotAssessed
                ? ['Kritinių ribų taisyklės neapibrėžtos. Rodiklių saugumas neįvertintas.']
                : []),
            'Tai nepatvirtinti DI demonstraciniai pasiūlymai. Dozės, alergijos ir sąveikos turi būti patikrintos. Tai nėra gydymo paskyrimas.',
        ],
    }
}

export default function BloodTestReview({ testId }: { testId: number }) {
    const [test, setTest] = useState<BloodTestDetail | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')

    const [openedIndicators, setOpenedIndicators] =
        useState<Record<number, boolean>>({})
    const [savingIndicatorId, setSavingIndicatorId] =
        useState<number | null>(null)
    const [isReextracting, setIsReextracting] = useState(false)
    const [isConfirming, setIsConfirming] = useState(false)
    const pollingStartedAt = useRef(0)

    const [analysisTestId, setAnalysisTestId] = useState<number | null>(null)

    const showAnalysis = analysisTestId === testId
    const [startingAnalysis, setStartingAnalysis] = useState(false)
    const [recommendations, setRecommendations] = useState<RecommendationPreviewResponse | null>(null)
    const [generatingRecommendations, setGeneratingRecommendations] = useState(false)
    const [savedRecommendations, setSavedRecommendations] = useState<LatestRecommendationsResponse | null>(null)
    const [savingRecommendationId, setSavingRecommendationId] = useState<number | null>(null)
    const [consumptionPlan, setConsumptionPlan] = useState<ConsumptionPlanData | null>(null)
    const [openingPlan, setOpeningPlan] = useState(false)

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
        let cancelled = false

        pollingStartedAt.current = Date.now()

        void api.get<BloodTestDetail>(
            `/api/tyrimai/${testId}`
        )
            .then(response => {
                if (cancelled) return

                setTest(response.data)
                setError('')
            })
            .catch(err => {
                if (!cancelled) {
                    setError(
                        getErrorMessage(
                            err,
                            'Nepavyko gauti tyrimo duomenų.'
                        )
                    )
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoading(false)
                }
            })

        return () => {
            cancelled = true
        }
    }, [testId])


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
            setOpenedIndicators({})
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
            setOpenedIndicators({})
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


    async function requestExplanation() {
        if (startingAnalysis) return

        setStartingAnalysis(true)
        setError('')

        try {
            await api.post(
                `/api/tyrimai/${testId}/analysis`
            )

            setAnalysisTestId(testId)

        } catch (err) {
            setError(getErrorMessage(
                err,
                'Nepavyko pradėti rezultatų analizės.'
            ))
        } finally {
            setStartingAnalysis(false)
        }
    }


    async function getLatestRecommendations(): Promise<LatestRecommendationsResponse | null> {
        try {
            const response = await api.get<LatestRecommendationsResponse>(
                `/api/tyrimai/${testId}/recommendations/latest`
            )
            return response.data
        } catch (err) {
            if (axios.isAxiosError(err) && err.response?.status === 404) {
                return null // Dar nieko negeneruota.
            }
            throw err
        }
    }

    // Pirmiausia rodome jau išsaugotas rekomendacijas, kad nesukurtume naujo
    // generavimo kas kartą naudotojui grįžus į šį langą.
    async function openRecommendations() {
        if (generatingRecommendations) return
        setGeneratingRecommendations(true)
        setError('')
        try {
            const latest = await getLatestRecommendations()
            if (latest) {
                setSavedRecommendations(latest)
                setRecommendations(previewFromLatest(latest, test?.indicators ?? [], recommendations))
                setAnalysisTestId(null)
            } else {
                await generateRecommendationsRequest()
            }
        } catch (err) {
            setError(getErrorMessage(err, 'Nepavyko atidaryti papildų rekomendacijų.'))
        } finally {
            setGeneratingRecommendations(false)
        }
    }

    async function generateRecommendationsRequest() {
        const response = await api.post<RecommendationPreviewResponse>(
            `/api/tyrimai/${testId}/recommendations/preview`
        )
        setRecommendations(response.data)
        setSavedRecommendations(null)
        setAnalysisTestId(null)
        // Naujas generavimas sukuria naujus įrašų ID; jų nebandome spėti iš indicatorId.
        const latest = await getLatestRecommendations()
        setSavedRecommendations(latest)
    }

    async function regenerateRecommendations() {
        if (generatingRecommendations || savingRecommendationId !== null) return
        if (savedRecommendations?.items.some(item => item.decision !== 'PENDING')
            && !window.confirm('Naujas generavimas taps aktyviu. Ankstesni pasirinkimai liks istorijoje, bet nebus naujausio generavimo dalis. Generuoti iš naujo?')) {
            return
        }

        setGeneratingRecommendations(true)
        setError('')
        try {
            await generateRecommendationsRequest()
        } catch (err) {
            setError(getErrorMessage(err, 'Nepavyko sugeneruoti papildų rekomendacijų.'))
        } finally {
            setGeneratingRecommendations(false)
        }
    }

    async function decideRecommendation(id: number, decision: 'ACCEPTED' | 'REJECTED') {
        if (savingRecommendationId !== null || generatingRecommendations) return
        setSavingRecommendationId(id)
        setError('')
        try {
            const response = await api.patch<SavedRecommendationItem>(
                `/api/tyrimai/${testId}/recommendations/${id}/decision`,
                { decision }
            )
            setSavedRecommendations(previous => previous ? {
                ...previous,
                items: previous.items.map(item => item.id === id ? response.data : item),
            } : previous)
        } catch (err) {
            if (axios.isAxiosError(err) && [404, 409].includes(err.response?.status ?? 0)) {
                try {
                    const latest = await getLatestRecommendations()
                    setSavedRecommendations(latest)
                    if (latest) {
                        setRecommendations(previewFromLatest(latest, test?.indicators ?? []))
                    }
                } catch {
                    // Paliekame originalią klaidą naudotojui.
                }
            }
            setError(getErrorMessage(err, 'Nepavyko išsaugoti rekomendacijos pasirinkimo.'))
        } finally {
            setSavingRecommendationId(null)
        }
    }

    async function openConsumptionPlan() {
        if (openingPlan || generatingRecommendations || savingRecommendationId !== null) return
        setOpeningPlan(true)
        setError('')
        try {
            // POST is idempotent: returns an existing plan or creates a new draft.
            // Avoid an expected GET 404 for a first-time plan.
            const response = await api.post<ConsumptionPlanData>(`/api/tyrimai/${testId}/plan/generate`)
            setConsumptionPlan(response.data)
        } catch (err) {
            setError(getErrorMessage(err, 'Nepavyko sudaryti vartojimo plano.'))
        } finally {
            setOpeningPlan(false)
        }
    }

    if (loading) return <p>Įkeliami tyrimo duomenys...</p>



    if (test?.status === 'CONFIRMED') {
        return (
            <>
                {consumptionPlan ? (
                    <ConsumptionPlan
                        data={consumptionPlan}
                        onChange={setConsumptionPlan}
                        onBack={() => setConsumptionPlan(null)}
                    />
                ) : recommendations ? (
                    <RecommendationPreview
                        data={recommendations}
                        onBack={() => setRecommendations(null)}
                        onRegenerate={() => void regenerateRecommendations()}
                        regenerating={generatingRecommendations}
                        saved={savedRecommendations}
                        onDecision={(id, decision) => void decideRecommendation(id, decision)}
                        savingRecommendationId={savingRecommendationId}
                        onOpenPlan={() => void openConsumptionPlan()}
                        openingPlan={openingPlan}
                    />
                ) : showAnalysis ? (
                    <>
                        <button
                            type="button"
                            onClick={() => setAnalysisTestId(null)}
                            className="review-pdf-button"
                        >
                            ← Grįžti prie rodiklių
                        </button>

                        <BloodTestAnalysis key={testId} testId={testId} />
                    </>
                ) : (
                    <BloodTestResults
                        test={test}
                        onOpenPdf={() => void openOriginalPdf()}
                        onExplain={() => void requestExplanation()}
                        explanationLoading={startingAnalysis}
                        onRecommendations={() => void openRecommendations()}
                        recommendationsLoading={generatingRecommendations}
                    />
                )}

                {error && (
                    <p role="alert" className="review-error">
                        {error}
                    </p>
                )}
            </>
        )
    }



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
                    <p className="review-confirmed" role="status">
                        Tyrimas patvirtintas. Rodikliai išsaugoti ir paruošti tolesnei analizei.
                    </p>
                )}
            </>}
        </section>
    )
}
