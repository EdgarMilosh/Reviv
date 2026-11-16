import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import './IndicatorTrendPage.css'

type IndicatorOption = {
    id: string
    name: string
    unit?: string | null
    referenceInterval?: string | null
}

type HistoryPoint = {
    testId: number
    date: string
    value: string
    unit?: string | null
    referenceInterval?: string | null
}

type HistoryResponse = {
    indicatorId: string
    name: string
    unit?: string | null
    referenceInterval?: string | null
    points: HistoryPoint[]
    hasEnoughData: boolean
}

const PERIOD_OPTIONS = [
    { value: '', label: 'Visi tyrimai' },
    { value: '1m', label: '1 mėn.' },
    { value: '3m', label: '3 mėn.' },
    { value: '6m', label: '6 mėn.' },
    { value: '1y', label: '1 metai' },
]

function toNumber(value: string | null | undefined): number | null {
    if (!value) return null
    const normalized = value.replace(',', '.').trim()
    const parsed = Number(normalized)
    return Number.isFinite(parsed) ? parsed : null
}

function parseReferenceRange(referenceInterval?: string | null): { min: number; max: number } | null {
    if (!referenceInterval) return null

    const normalized = referenceInterval.replace(/[–—]/g, '-').replace(/\s+/g, ' ').trim()
    const match = normalized.match(/(-?\d+(?:[.,]\d+)?)\s*-\s*(-?\d+(?:[.,]\d+)?)/)
    if (!match) return null

    const min = Number(match[1].replace(',', '.'))
    const max = Number(match[2].replace(',', '.'))
    if (!Number.isFinite(min) || !Number.isFinite(max)) return null

    return { min: Math.min(min, max), max: Math.max(min, max) }
}

function formatDate(value: string): string {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleDateString('lt-LT', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function formatShortDate(value: string): string {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleDateString('lt-LT', {
        year: 'numeric',
        day: '2-digit',
        month: '2-digit'
    })
}

export default function IndicatorTrendPage() {
    const [indicators, setIndicators] = useState<IndicatorOption[]>([])
    const [selectedIndicatorId, setSelectedIndicatorId] = useState('')
    const [period, setPeriod] = useState('')
    const [history, setHistory] = useState<HistoryResponse | null>(null)
    const [selectedPoint, setSelectedPoint] = useState<HistoryPoint | null>(null)
    const [loadingIndicators, setLoadingIndicators] = useState(true)
    const [loadingHistory, setLoadingHistory] = useState(false)
    const [error, setError] = useState('')

    useEffect(() => {
        let cancelled = false

        api.get<IndicatorOption[]>('/api/rodikliai')
            .then((response) => {
                if (cancelled) return
                setIndicators(response.data)
                if (response.data.length > 0) {
                    setSelectedIndicatorId((current) => current || response.data[0].id)
                }
                setError('')
            })
            .catch(() => {
                if (!cancelled) {
                    setError('Nepavyko gauti rodiklių sąrašo.')
                }
            })
            .finally(() => {
                if (!cancelled) setLoadingIndicators(false)
            })

        return () => {
            cancelled = true
        }
    }, [])



    useEffect(() => {
        if (!selectedIndicatorId) {
            return
        }

        let cancelled = false

        const fetchHistory = async () => {
            setLoadingHistory(true)

            const params = period ? { period } : undefined

            try {
                const response = await api.get<HistoryResponse>(
                    `/api/rodikliai/${selectedIndicatorId}/istorija`,
                    { params }
                )

                if (cancelled) return

                setHistory(response.data)
                setSelectedPoint(response.data.points.at(-1) ?? null)
                setError('')
            } catch {
                if (cancelled) return

                setError('Nepavyko gauti rodiklio istorijos.')
                setHistory(null)
                setSelectedPoint(null)
            } finally {
                if (!cancelled) {
                    setLoadingHistory(false)
                }
            }
        }

        fetchHistory()

        return () => {
            cancelled = true
        }
    }, [selectedIndicatorId, period])

    const chart = useMemo(() => {
        if (!history || history.points.length === 0) {
            return null
        }

        const values = history.points
            .map((point) => toNumber(point.value))
            .filter((value): value is number => value !== null)

        const referenceRange = parseReferenceRange(history.referenceInterval)
        const chartValues = referenceRange
            ? [...values, referenceRange.min, referenceRange.max]
            : values

        const minValue = chartValues.length > 0 ? Math.min(...chartValues) : 0
        const maxValue = chartValues.length > 0 ? Math.max(...chartValues) : 0
        const paddedMin = Number.isFinite(minValue) ? Math.min(0, minValue * 0.95) : 0
        const paddedMax = Number.isFinite(maxValue) ? Math.max(0, maxValue * 1.05) : 0
        const spread = paddedMax - paddedMin || 1

        const width = 860
        const height = 360
        const margin = { top: 20, right: 45, bottom: 54, left: 75 }
        const plotLeft = margin.left + 38
        const plotRight = width - margin.right - 38
        const plotWidth = plotRight - plotLeft
        const plotHeight = height - margin.top - margin.bottom

        const toY = (value: number) => margin.top + (1 - (value - paddedMin) / spread) * plotHeight


        const pointCount = history.points.length

        // Stulpelių grupė užima tik tiek vietos, kiek reikia.
        // Daugėjant tyrimų, grupė gali išsiplėsti per visą plotį.
        const slotWidth = Math.min(100, plotWidth / pointCount)
        const groupWidth = slotWidth * pointCount
        const groupLeft = plotLeft + (plotWidth - groupWidth) / 2

        const bars = history.points.map((point, index) => {
            const value = toNumber(point.value)
            if (value === null) return null

            // Stulpeliai centruojami grupės viduje.
            const x = groupLeft + index * slotWidth + slotWidth / 2

            // Stulpelio plotis prisitaiko prie tyrimų skaičiaus.
            const barWidth = Math.min(54, slotWidth * 0.78)

            const y = toY(value)
            const barHeight = Math.max(4, height - margin.bottom - y)

            return {
                ...point,
                x: x - barWidth / 2,
                y,
                width: barWidth,
                height: barHeight,
                isSelected: selectedPoint?.testId === point.testId,
            }
        }).filter(Boolean) as Array<{
            testId: number
            date: string
            value: string
            unit?: string | null
            referenceInterval?: string | null
            x: number
            y: number
            width: number
            height: number
            isSelected: boolean
        }>


        const baselineY = referenceRange ? toY(referenceRange.max) : null
        const baselineHeight = referenceRange ? Math.abs(toY(referenceRange.max) - toY(referenceRange.min)) : 0

        return {
            width,
            height,
            margin,
            plotWidth,
            plotHeight,
            bars,
            referenceRange,
            baselineY,
            baselineHeight,
            toY,
            yTicks: Array.from({ length: 5 }, (_, index) => {
                const tickValue = paddedMin + (spread / 4) * index
                return {
                    value: tickValue,
                    y: toY(tickValue),
                }
            }).reverse(),
        }
    }, [history, selectedPoint])

    const selectedIndicator = indicators.find((indicator) => indicator.id === selectedIndicatorId)

    return (
        <section className="trend-page">
            <div className="trend-header">
                <div>
                    <p className="eyebrow">Stebėjimas</p>
                    <h1>Rodiklių kitimas</h1>
                </div>
            </div>

            {error && (
                <div className="trend-message trend-error" role="alert">
                    {error}
                </div>
            )}

            <div className="trend-controls">
                <label className="trend-field">
                    <span>Rodiklis</span>
                    <select
                        value={selectedIndicatorId}
                        onChange={(event) => setSelectedIndicatorId(event.target.value)}
                        disabled={loadingIndicators || indicators.length === 0}
                    >
                        {indicators.length === 0 ? (
                            <option value="">Nėra rodiklių</option>
                        ) : (
                            indicators.map((indicator) => (
                                <option key={indicator.id} value={indicator.id}>
                                    {indicator.name}
                                </option>
                            ))
                        )}
                    </select>
                </label>

                <label className="trend-field">
                    <span>Laikotarpis</span>
                    <select value={period} onChange={(event) => setPeriod(event.target.value)}>
                        {PERIOD_OPTIONS.map((option) => (
                            <option key={option.value || 'all'} value={option.value}>
                                {option.label}
                            </option>
                        ))}
                    </select>
                </label>
            </div>

            {loadingIndicators && <p className="trend-message">Kraunami rodikliai...</p>}

            {!loadingIndicators && indicators.length === 0 && !error && (
                <div className="trend-empty">
                    <p>Šiuo metu nėra jokių jūsų tyrimų rodiklių, kuriuos būtų galima palyginti.</p>
                </div>
            )}

            {!loadingIndicators && selectedIndicator && history && chart && (
                <>
                    <div className="trend-summary">
                        <div>
                            <span className="summary-label">Rodiklis</span>
                            <strong>{history.name}</strong>
                        </div>
                        <div>
                            <span className="summary-label">Matavimo vienetas</span>
                            <strong>{history.unit || selectedIndicator.unit || '—'}</strong>
                        </div>
                        <div>
                            <span className="summary-label">Referencinis intervalas</span>
                            <strong>{history.referenceInterval || 'Nenurodyta'}</strong>
                        </div>
                    </div>

                    {history.hasEnoughData ? (
                        <div className="trend-chart-card">
                                    <svg viewBox={`0 0 ${chart.width} ${chart.height}`} className="trend-chart" role="img" aria-label={`Rodiklio ${history.name} kitimo diagrama`}>
                                {chart.yTicks.map((tick) => (
                                    <g key={String(tick.value)}>
                                        <line x1={chart.margin.left} y1={tick.y} x2={chart.width - chart.margin.right} y2={tick.y} stroke="#e2e8f0" strokeDasharray="4 4" />
                                        <text x={chart.margin.left - 10} y={tick.y + 4} textAnchor="end" fill="#64748b" fontSize="12">
                                            {tick.value.toFixed(0)}
                                        </text>
                                    </g>
                                ))}

                                {chart.referenceRange && (
                                    <rect
                                        x={chart.margin.left + 38}
                                        y={chart.baselineY ?? chart.margin.top}
                                        width={chart.plotWidth}
                                        height={Math.max(chart.baselineHeight, 12)}
                                        fill="#a7f3d0"
                                        opacity={0.7}
                                    />
                                )}

                                {chart.bars.map((bar) => (
                                    <g key={bar.testId}>
                                        <rect
                                            x={bar.x}
                                            y={bar.y}
                                            width={bar.width}
                                            height={bar.height}
                                            rx={0}
                                            fill={bar.isSelected ? '#617e10' : '#0f766e'}
                                            opacity={1}
                                            cursor="pointer"
                                            onClick={() => setSelectedPoint(bar)}
                                        />
                                        <text
                                            x={bar.x + bar.width / 2}
                                            y={Math.max(bar.y - 8, 14)}
                                            textAnchor="middle"
                                            fill='#000000'
                                            fontSize="12"
                                            fontWeight="600"
                                        >
                                            {bar.value}
                                        </text>
                                        <text
                                            x={bar.x + bar.width / 2}
                                            y={chart.height - 28}
                                            textAnchor="middle"
                                            fill="#475569"
                                            fontSize="11"
                                        >
                                            {formatShortDate(bar.date)}
                                        </text>
                                    </g>
                                ))}

                                <line x1={chart.margin.left + 38} y1={chart.height - chart.margin.bottom} x2={chart.width - chart.margin.right - 38} y2={chart.height - chart.margin.bottom} stroke="#475569" />
                                <line x1={chart.margin.left + 38} y1={chart.margin.top} x2={chart.margin.left + 38} y2={chart.height - chart.margin.bottom} stroke="#475569" />
                            </svg>
                        </div>
                    ) : (
                        <div className="trend-message trend-warning">
                            Reikalingi bent du tyrimai, kad būtų galima palyginti rodiklio pokytį laike.
                        </div>
                    )}

                    {selectedPoint && (
                        <div className="trend-selection">
                            <div>
                                <span>Pasirinktas tyrimas</span>
                                <strong>{formatDate(selectedPoint.date)}</strong>
                            </div>
                            <div>
                                <span>Reikšmė</span>
                                <strong>{selectedPoint.value} {selectedPoint.unit || history.unit || ''}</strong>
                            </div>
                            <Link className="trend-link" to={`/upload?testId=${selectedPoint.testId}`}>
                                Atidaryti tyrimą
                            </Link>
                        </div>
                    )}
                </>
            )}

            {!loadingHistory && selectedIndicator && history && !chart && (
                <div className="trend-message">Nėra duomenų rodyti pasirinktam laikotarpiui.</div>
            )}
        </section>
    )
}
