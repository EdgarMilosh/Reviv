import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import './BloodTestAnalysis.css'

type AnalysisStatus =
    | 'NOT_REQUESTED'
    | 'QUEUED'
    | 'PROCESSING'
    | 'COMPLETED'
    | 'FAILED'

type IndicatorResult = {
    indicatorId: number
    name: string
    rawValue: string | null
    unit: string | null
    referenceInterval: string | null
    status: string
    deviationPercent: number | null
    criticalStatus: string
    meaning: string | null
    possibleCauses: string[]
    practicalAdvice: string | null
}

type AnalysisResult = {
    bloodTestId: number
    status: AnalysisStatus
    aiModel: string | null
    normalCount: number
    indicators: IndicatorResult[]
}

export default function BloodTestAnalysis({
                                              testId
                                          }: {
    testId: number
}) {
    const [analysis, setAnalysis] =
        useState<AnalysisResult | null>(null)

    const [loading, setLoading] = useState(true)
    const [starting, setStarting] = useState(false)
    const [error, setError] = useState('')

    const loadAnalysis = useCallback(async () => {
        const response = await api.get<AnalysisResult>(
            `/api/tyrimai/${testId}/analysis`
        )

        setAnalysis(response.data)
        setError('')
    }, [testId])

    useEffect(() => {
        setAnalysis(null)
        setLoading(true)

        void loadAnalysis()
            .catch(() => {
                setError('Nepavyko gauti analizės duomenų.')
            })
            .finally(() => setLoading(false))
    }, [loadAnalysis])


    useEffect(() => {
        if (
            analysis?.status !== 'QUEUED' &&
            analysis?.status !== 'PROCESSING'
        ) {
            return
        }

        const interval = window.setInterval(() => {
            void loadAnalysis().catch(() => {
                setError('Nepavyko atnaujinti analizės būsenos.')
            })
        }, 3000)

        return () => window.clearInterval(interval)
    }, [analysis?.status, loadAnalysis])

    async function startAnalysis() {
        if (starting || analysis?.status !== 'NOT_REQUESTED') {
            return
        }

        setStarting(true)
        setError('')

        try {
            await api.post(
                `/api/tyrimai/${testId}/analysis`
            )

            await loadAnalysis()
        } catch {
            setError('Nepavyko pradėti DI analizės.')
        } finally {
            setStarting(false)
        }
    }

    if (loading) {
        return <p>Įkeliama analizės informacija...</p>
    }

    return (
        <section className="blood-test-analysis">
            <h2>Rezultatų paaiškinimas</h2>

            {error && <p role="alert">{error}</p>}


            {analysis && analysis.status !== 'COMPLETED' && (
                <div className="analysis-indicators">
                    {analysis.indicators.length === 0 ? (
                        <p>Nukrypusių rodiklių nenustatyta.</p>
                    ) : (
                        analysis.indicators.map(indicator => (
                            <div
                                key={indicator.indicatorId}
                                className={
                                    indicator.criticalStatus === 'CRITICAL'
                                        ? 'analysis-indicator analysis-indicator-critical'
                                        : 'analysis-indicator'
                                }
                            >
                                <h3>
                                    {indicator.name} – {indicator.rawValue ?? '–'}{' '}
                                    {indicator.unit ?? ''}
                                </h3>

                                <p className="analysis-indicator-status">
                                    {indicator.status === 'BELOW'
                                        ? 'Žemiau intervalo'
                                        : indicator.status === 'ABOVE'
                                            ? 'Aukščiau intervalo'
                                            : 'Nepavyko įvertinti'}

                                    {indicator.deviationPercent !== null
                                        ? ` – ${indicator.deviationPercent}%`
                                        : ''}
                                </p>
                                <p>
                                    Referencinis intervalas:{' '}
                                    {indicator.referenceInterval ?? 'Nenurodytas'}
                                </p>

                                {indicator.criticalStatus === 'CRITICAL' && (
                                    <p role="alert">
                                        Kritinis įspėjimas. Reikia skubaus
                                        sveikatos priežiūros specialisto įvertinimo.
                                    </p>
                                )}

                                {indicator.criticalStatus === 'NOT_ASSESSED' && (
                                    <p>
                                        Kritiškumas dar neįvertintas.
                                    </p>
                                )}
                            </div>
                        ))
                    )}
                </div>
            )}


            {analysis?.status === 'NOT_REQUESTED' && (
                <button
                    type="button"
                    disabled={starting}
                    onClick={() => void startAnalysis()}
                >
                    {starting
                        ? 'Siunčiama...'
                        : 'Gauti paaiškinimą'}
                </button>
            )}

            {analysis?.status === 'QUEUED' && (
                <p role="status">
                    Analizė laukia vykdymo eilėje...
                </p>
            )}

            {analysis?.status === 'PROCESSING' && (
                <p role="status">
                    GPT generuoja rezultatų paaiškinimus...
                </p>
            )}

            {analysis?.status === 'FAILED' && (
                <p role="alert">
                    Analizės atlikti nepavyko.
                    Nauja mokama užklausa automatiškai
                    nesiunčiama.
                </p>
            )}

            {analysis?.status === 'COMPLETED' && (
                <>
                    {analysis.indicators.map(indicator => (
                        <div
                            key={indicator.indicatorId}
                            className={
                                indicator.criticalStatus === 'CRITICAL'
                                    ? 'analysis-indicator analysis-indicator-critical'
                                    : 'analysis-indicator'
                            }
                        >
                            {indicator.criticalStatus === 'CRITICAL' ? (
                                <p role="alert">
                                    <strong>{indicator.name}</strong>:
                                    nustatytas kritinis nukrypimas.
                                    Rekomenduojame nedelsiant
                                    kreiptis į sveikatos priežiūros
                                    specialistą.
                                </p>
                            ) : (
                                <>
                                    <h3>
                                        {indicator.name} –{' '}
                                        {indicator.rawValue}{' '}
                                        {indicator.unit}
                                    </h3>

                                    <p className="analysis-indicator-status">
                                        {indicator.status === 'BELOW'
                                            ? 'Žemiau intervalo'
                                            : indicator.status === 'ABOVE'
                                                ? 'Aukščiau intervalo'
                                                : 'Nepavyko įvertinti'}

                                        {indicator.deviationPercent !== null
                                            ? ` – ${indicator.deviationPercent}%`
                                            : ''}
                                    </p>

                                    {indicator.criticalStatus ===
                                        'NOT_ASSESSED' && (
                                            <p>
                                                Kritiškumas neįvertintas.
                                            </p>
                                        )}

                                    {indicator.meaning && (
                                        <>
                                            <p>{indicator.meaning}</p>

                                            <ul>
                                                {indicator.possibleCauses.map(
                                                    (cause, index) => (
                                                        <li key={index}>
                                                            {cause}
                                                        </li>
                                                    )
                                                )}
                                            </ul>

                                            {indicator.practicalAdvice && (
                                                <p>
                                                    {indicator.practicalAdvice}
                                                </p>
                                            )}

                                            <span className="analysis-ai-label">
                                                Sugeneruota DI
                                            </span>
                                        </>
                                    )}
                                </>
                            )}
                        </div>
                    ))}

                    {analysis.indicators.length === 0 && (
                        <p>
                            Nukrypusių rodiklių nenustatyta.
                        </p>
                    )}

                    <p className="analysis-disclaimer">
                        Ši informacija yra informacinio pobūdžio,
                        nėra medicininė diagnozė ir nepakeičia
                        gydytojo konsultacijos.
                    </p>
                </>
            )}
        </section>
    )
}
