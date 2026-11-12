import { useState } from 'react'
import './BloodTestResults.css'

export type IndicatorStatus =
    | 'BELOW_RANGE'
    | 'WITHIN_RANGE'
    | 'ABOVE_RANGE'
    | 'UNDETERMINED'

export type BloodIndicator = {
    id: number
    name: string
    rawValue: string | null
    unit: string | null
    referenceInterval: string | null
    indicatorStatus: IndicatorStatus
}

type Props = {
    test: {
        testDate: string | null
        laboratory: string | null
        indicators: BloodIndicator[]
    }
    onOpenPdf: () => void
}

const statusLabels: Record<IndicatorStatus, string> = {
    BELOW_RANGE: 'Žemiau intervalo',
    WITHIN_RANGE: 'Intervale',
    ABOVE_RANGE: 'Aukščiau intervalo',
    UNDETERMINED: 'Neįvertinta'
}

export default function BloodTestResults({
                                             test,
                                             onOpenPdf
                                         }: Props) {
    const [showAll, setShowAll] = useState(false)

    // Pagal nutylejima paslepiame normalius rodiklius
    const displayedIndicators = test.indicators.filter(
        indicator =>
            showAll ||
            indicator.indicatorStatus !== 'WITHIN_RANGE'
    )

    return (
        <section className="blood-test-results">
            <h2>
                Tyrimas · {test.testDate ?? 'Data nenurodyta'}
                {' · '}
                {test.laboratory ?? 'Laboratorija nenurodyta'}
            </h2>

            <label>
                <input
                    type="checkbox"
                    checked={showAll}
                    onChange={e => setShowAll(e.target.checked)}
                />
                Rodyti visus rodiklius
            </label>

            {displayedIndicators.length === 0 ? (
                <p>Nukrypusių rodiklių nenustatyta.</p>
            ) : (
                <table className="results-table">
                    <thead>
                    <tr>
                        <th>Rodiklis</th>
                        <th>Reikšmė</th>
                        <th>Intervalas</th>
                        <th>Būsena</th>
                    </tr>
                    </thead>

                    <tbody>
                    {displayedIndicators.map(indicator => (
                        <tr key={indicator.id}>
                            <td>{indicator.name}</td>
                            <td>
                                {indicator.rawValue ?? '–'}
                                {' '}
                                {indicator.unit ?? ''}
                            </td>
                            <td>
                                {indicator.referenceInterval ?? '–'}
                            </td>
                            <td>
                                    <span className={
                                        `results-status results-status--${indicator.indicatorStatus.toLowerCase()}`
                                    }>
                                        {statusLabels[indicator.indicatorStatus] ?? 'Neįvertinta'}
                                    </span>
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            )}

            <div className="results-actions">
                <button type="button" disabled>
                    Gauti paaiškinimą
                </button>

                <button type="button" disabled>
                    Rodiklių kitimas
                </button>

                <button type="button" onClick={onOpenPdf}>
                    Peržiūrėti originalų PDF
                </button>
            </div>
        </section>
    )
}
