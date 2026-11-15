import './RecommendationPreview.css'

export type RecommendationItem = {
    indicatorId: number
    indicatorName: string
    supplementId: number
    supplementName: string
    activeSubstance: string
    form: string
    proposedDailyDose: number | null
    doseUnit: string | null
    durationDays: number | null
    reason: string
    warnings: string[]
}

export type NotRecommendedItem = {
    indicatorId: number
    indicatorName: string
    status: 'NOT_RECOMMENDED' | 'NEEDS_REVIEW'
    reason: string
    warnings: string[]
}

export type RecommendationDecision = 'PENDING' | 'ACCEPTED' | 'REJECTED'

export type SavedRecommendationItem = {
    id: number
    indicatorId: number
    supplementId: number | null
    supplementName: string | null
    recommendationStatus: 'SUGGESTED' | 'NOT_RECOMMENDED' | 'NEEDS_REVIEW'
    decision: RecommendationDecision
    proposedDailyDose: number | null
    doseUnit: string | null
    durationDays: number | null
    reason: string
    warnings: string[]
    needsSpecialistReview: boolean
    decidedAt: string | null
}

export type LatestRecommendationsResponse = {
    generationId: number
    bloodTestId: number
    generatedAt: string
    criticalityNotAssessed: boolean
    medicallyVerified: boolean
    items: SavedRecommendationItem[]
}

export type RecommendationPreviewResponse = {
    bloodTestId: number
    criticalDetected: boolean
    criticalityNotAssessed: boolean
    normalCount: number
    undeterminedCount: number
    canAccept: boolean
    recommendations: RecommendationItem[]
    notRecommended: NotRecommendedItem[]
    notices: string[]
}

const forms: Record<string, string> = {
    CAPSULE: 'Kapsulė',
    TABLET: 'Tabletė',
    POWDER: 'Milteliai',
    LIQUID: 'Skystis',
}

function formatDuration(days: number | null): string {
    if (days == null) return 'Trukmė nenustatyta'
    if (days % 7 === 0) return `${days / 7} sav.`
    return `${days} d.`
}

type Props = {
    data: RecommendationPreviewResponse
    onBack: () => void
    onRegenerate: () => void
    regenerating: boolean
    saved: LatestRecommendationsResponse | null
    onDecision: (recommendationId: number, decision: 'ACCEPTED' | 'REJECTED') => void
    savingRecommendationId: number | null
}

export default function RecommendationPreview({
    data,
    onBack,
    onRegenerate,
    regenerating,
    saved,
    onDecision,
    savingRecommendationId,
}: Props) {
    const decisionsByIndicator = new Map(
        saved?.items.map(item => [item.indicatorId, item]) ?? []
    )
    return (
        <section className="rec-page">
            <button className="rec-back" type="button" onClick={onBack}>
                ← Grįžti prie rodiklių
            </button>

            <div className="rec-heading">
                <h2>Papildų rekomendacijos</h2>
                <button
                    type="button"
                    className="rec-regenerate"
                    onClick={onRegenerate}
                    disabled={regenerating}
                >
                    {regenerating ? 'Generuojama...' : 'Generuoti iš naujo'}
                </button>
            </div>

            <p className="rec-intro">
                Pasiūlymai generuojami tik rodikliams žemiau referencinio intervalo.
                Modelis vertina pateiktą profilį ir papildų katalogą.
            </p>

            {data.criticalDetected && (
                <div className="rec-alert rec-alert-danger" role="alert">
                    Aptiktas kritinis nukrypimas. Papildų pasiūlymai nepateikiami;
                    reikalingas sveikatos priežiūros specialisto įvertinimas.
                </div>
            )}

            <div className="rec-grid">
                {data.recommendations.map(item => (
                    <article className="rec-card" key={item.indicatorId}>
                        <div className="rec-card-content">
                            <h3>{item.supplementName}</h3>
                            <p className="rec-meta">
                                {[
                                    item.proposedDailyDose != null && item.doseUnit
                                        ? `${item.proposedDailyDose} ${item.doseUnit} per parą`
                                        : 'Dozė nenustatyta',
                                    item.form ? (forms[item.form] ?? item.form) : '',
                                    formatDuration(item.durationDays),
                                ].filter(Boolean).join(' · ')}
                            </p>
                            <p className="rec-indicator">Rodiklis: {item.indicatorName}</p>
                            <p className="rec-reason">{item.reason}</p>
                            {item.activeSubstance && (
                                <p className="rec-substance">Veiklioji medžiaga: {item.activeSubstance}</p>
                            )}
                        </div>

                        {item.warnings.length > 0 && (
                            <div className="rec-alert rec-alert-warning">
                                <strong>Įspėjimai</strong>
                                <ul>
                                    {item.warnings.map((warning, index) => (
                                        <li key={`${index}-${warning}`}>{warning}</li>
                                    ))}
                                </ul>
                            </div>
                        )}

                        {(() => {
                            const savedItem = decisionsByIndicator.get(item.indicatorId)
                            const pending = savedItem?.recommendationStatus === 'SUGGESTED'
                                && savedItem.decision === 'PENDING'
                            const disabled = !pending || regenerating || savingRecommendationId !== null
                            return (
                                <>
                                    {savedItem?.decision === 'ACCEPTED' && (
                                        <p className="rec-choice rec-choice-accepted" role="status">
                                            Priimta plano juodraščiui (ne medicininis patvirtinimas).
                                        </p>
                                    )}
                                    {savedItem?.decision === 'REJECTED' && (
                                        <p className="rec-choice rec-choice-rejected" role="status">
                                            Atmesta.
                                        </p>
                                    )}
                                    <div className="rec-actions">
                                        <button
                                            type="button"
                                            disabled={disabled}
                                            onClick={() => savedItem && onDecision(savedItem.id, 'ACCEPTED')}
                                            title={!savedItem ? 'Ši rekomendacija dar neišsaugota.' : undefined}
                                        >
                                            {savingRecommendationId === savedItem?.id ? 'Saugoma...' : 'Priimti'}
                                        </button>
                                        <button
                                            type="button"
                                            disabled={disabled}
                                            onClick={() => savedItem && onDecision(savedItem.id, 'REJECTED')}
                                            title={!savedItem ? 'Ši rekomendacija dar neišsaugota.' : undefined}
                                        >
                                            Atmesti
                                        </button>
                                    </div>
                                </>
                            )
                        })()}
                    </article>
                ))}

                {data.notRecommended.map(item => (
                    <article className="rec-card rec-card-muted" key={item.indicatorId}>
                        <h3>{item.indicatorName}</h3>
                        <p className="rec-decision">
                            {item.status === 'NEEDS_REVIEW'
                                ? 'Reikalingas papildomas įvertinimas'
                                : 'Papildas nesiūlomas'}
                        </p>
                        <p>{item.reason}</p>
                        {item.warnings.length > 0 && (
                            <div className="rec-alert rec-alert-warning">
                                <ul>
                                    {item.warnings.map((warning, index) => (
                                        <li key={`${index}-${warning}`}>{warning}</li>
                                    ))}
                                </ul>
                            </div>
                        )}
                    </article>
                ))}
            </div>

            {data.recommendations.length === 0 && data.notRecommended.length === 0 && !data.criticalDetected && (
                <p className="rec-empty">Šiam tyrimui papildų pasiūlymų nėra.</p>
            )}

            {data.undeterminedCount > 0 && (
                <div className="rec-normal">
                    {data.undeterminedCount} rodiklių būsenos nepavyko įvertinti.
                </div>
            )}

            {data.notices.length > 0 && (
                <div className="rec-notices" role="status">
                    {data.notices.map((notice, index) => (
                        <p key={`${index}-${notice}`}>{notice}</p>
                    ))}
                </div>
            )}

            <p className="rec-generated">Sugeneruota DI · nepatvirtinti demonstraciniai pasiūlymai</p>
        </section>
    )
}
