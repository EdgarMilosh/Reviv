import { useEffect, useState } from 'react'
import axios from 'axios'
import { api } from '../api'
import { shiftDate } from '../dates'
import './DoseTracker.css'

type DoseState = 'PENDING' | 'TAKEN' | 'SKIPPED'
type MealRelation = 'BEFORE_MEAL' | 'WITH_MEAL' | 'AFTER_MEAL' | 'INDEPENDENT' | 'UNKNOWN'

type ScheduledDose = {
    entryId: number
    supplementName: string
    dose: number
    doseUnit: string
    mealRelation: MealRelation
    time: string
    state: DoseState
    markedAt: string | null
    canCancel: boolean
}

type DaySchedule = {
    date: string
    planId: number
    planStatus: string
    doses: ScheduledDose[]
}

type Guidance = {
    action: 'DRINK_NOW' | 'SKIP'
    message: string
    supplementRule: string | null
}

type MarkResponse = {
    dose: ScheduledDose
    guidance: Guidance | null
}

type EntryHistory = {
    entryId: number
    supplementName: string
    planned: number
    taken: number
    skipped: number
    unmarked: number
    adherencePercent: number | null
}

type HistorySummary = {
    planId: number
    from: string
    to: string
    planned: number
    taken: number
    skipped: number
    unmarked: number
    adherencePercent: number | null
    cycleEnded: boolean
    notFollowed: boolean
    notice: string | null
    entries: EntryHistory[]
}

type Period = '7' | '30' | 'all'

const mealLabels: Record<MealRelation, string> = {
    BEFORE_MEAL: 'prieš valgį',
    WITH_MEAL: 'su maistu',
    AFTER_MEAL: 'po valgio',
    INDEPENDENT: '',
    UNKNOWN: '',
}

const stateLabels: Record<DoseState, string> = {
    PENDING: 'Laukiama',
    TAKEN: 'Išgerta',
    SKIPPED: 'Praleista',
}

const periods: { value: Period; label: string }[] = [
    { value: '7', label: 'Paskutinės 7 dienos' },
    { value: '30', label: 'Paskutinės 30 dienų' },
    { value: 'all', label: 'Visas ciklas' },
]

function errorMessage(error: unknown, fallback: string) {
    if (axios.isAxiosError(error)) {
        const data = error.response?.data as { detail?: string; message?: string } | undefined
        return data?.detail ?? data?.message ?? fallback
    }
    return 'Nepavyko susisiekti su serveriu.'
}

function formatDate(iso: string) {
    return new Date(`${iso}T00:00:00`).toLocaleDateString('lt-LT', { weekday: 'long', day: 'numeric', month: 'long' })
}

function formatDose(dose: ScheduledDose) {
    const meal = mealLabels[dose.mealRelation]
    return `${dose.dose} ${dose.doseUnit}${meal ? `, ${meal}` : ''}`
}

/** US-13: today's dose schedule with "taken" / "missed" marks, guidance after a missed dose and the dose history. */
export default function DoseTracker({ planId }: { planId: number }) {
    const [schedule, setSchedule] = useState<DaySchedule | null>(null)
    const [scheduleError, setScheduleError] = useState('')
    const [guidance, setGuidance] = useState<Record<number, Guidance>>({})
    const [busyEntry, setBusyEntry] = useState<number | null>(null)
    const [actionError, setActionError] = useState('')
    const [period, setPeriod] = useState<Period>('7')
    const [history, setHistory] = useState<HistorySummary | null>(null)
    const [historyError, setHistoryError] = useState('')
    const [historyVersion, setHistoryVersion] = useState(0)

    useEffect(() => {
        let active = true
        api.get<DaySchedule>(`/api/planai/${planId}/schedule`)
            .then(response => {
                if (active) {
                    setSchedule(response.data)
                    setScheduleError('')
                }
            })
            .catch(error => {
                if (active) setScheduleError(errorMessage(error, 'Nepavyko užkrauti dienos tvarkaraščio.'))
            })
        return () => {
            active = false
        }
    }, [planId])

    const today = schedule?.date

    useEffect(() => {
        if (!today) return
        let active = true
        const params = period === 'all' ? {} : { from: shiftDate(today, -(Number(period) - 1)), to: today }
        api.get<HistorySummary>(`/api/planai/${planId}/history`, { params })
            .then(response => {
                if (active) {
                    setHistory(response.data)
                    setHistoryError('')
                }
            })
            .catch(error => {
                if (active) setHistoryError(errorMessage(error, 'Nepavyko užkrauti vartojimo istorijos.'))
            })
        return () => {
            active = false
        }
    }, [planId, period, today, historyVersion])

    function replaceDose(updated: ScheduledDose) {
        setSchedule(current => current && {
            ...current,
            doses: current.doses.map(dose => (dose.entryId === updated.entryId ? updated : dose)),
        })
    }

    async function mark(dose: ScheduledDose, status: 'TAKEN' | 'SKIPPED') {
        if (!schedule) return
        setBusyEntry(dose.entryId)
        setActionError('')
        try {
            const response = await api.put<MarkResponse>(
                `/api/planai/${planId}/entries/${dose.entryId}/doses/${schedule.date}`, { status })
            replaceDose(response.data.dose)
            setGuidance(current => {
                const next = { ...current }
                if (response.data.guidance) next[dose.entryId] = response.data.guidance
                else delete next[dose.entryId]
                return next
            })
            setHistoryVersion(version => version + 1)
        } catch (error) {
            setActionError(errorMessage(error, 'Nepavyko išsaugoti žymėjimo.'))
        } finally {
            setBusyEntry(null)
        }
    }

    async function cancel(dose: ScheduledDose) {
        if (!schedule) return
        setBusyEntry(dose.entryId)
        setActionError('')
        try {
            await api.delete(`/api/planai/${planId}/entries/${dose.entryId}/doses/${schedule.date}`)
            replaceDose({ ...dose, state: 'PENDING', markedAt: null, canCancel: false })
            setGuidance(current => {
                const next = { ...current }
                delete next[dose.entryId]
                return next
            })
            setHistoryVersion(version => version + 1)
        } catch (error) {
            setActionError(errorMessage(error, 'Nepavyko atšaukti žymėjimo.'))
        } finally {
            setBusyEntry(null)
        }
    }

    const allDone = schedule !== null && schedule.doses.length > 0 && schedule.doses.every(dose => dose.state !== 'PENDING')

    return (
        <section className="dose-tracker" aria-label="Dozių žymėjimas">
            <h2>Šiandienos tvarkaraštis</h2>
            {schedule && <p className="dose-date">{formatDate(schedule.date)}</p>}

            {!schedule && !scheduleError && <p role="status">Kraunama…</p>}
            {scheduleError && <p className="dose-error" role="alert">{scheduleError}</p>}
            {schedule && schedule.doses.length === 0 && <p className="dose-muted">Šiandien dozių nėra.</p>}
            {allDone && <p className="dose-all-done" role="status">Visos šiandienos dozės pažymėtos.</p>}
            {actionError && <p className="dose-error" role="alert">{actionError}</p>}

            {schedule && schedule.doses.length > 0 && (
                <ul className="dose-list">
                    {schedule.doses.map(dose => (
                        <li key={dose.entryId} className={`dose-row is-${dose.state.toLowerCase()}`}>
                            <span className="dose-time">{dose.time.slice(0, 5)}</span>
                            <span className="dose-info">
                                <strong>{dose.supplementName}</strong>
                                <small>{formatDose(dose)}</small>
                            </span>
                            <span className={`dose-badge is-${dose.state.toLowerCase()}`}>{stateLabels[dose.state]}</span>
                            <span className="dose-actions">
                                {dose.state === 'PENDING' ? (
                                    <>
                                        <button type="button" className="btn small" disabled={busyEntry === dose.entryId}
                                                onClick={() => void mark(dose, 'TAKEN')}>Išgėriau</button>
                                        <button type="button" className="btn secondary small" disabled={busyEntry === dose.entryId}
                                                onClick={() => void mark(dose, 'SKIPPED')}>Praleidau</button>
                                    </>
                                ) : (
                                    dose.canCancel && (
                                        <button type="button" className="btn secondary small" disabled={busyEntry === dose.entryId}
                                                onClick={() => void cancel(dose)}>Atšaukti žymėjimą</button>
                                    )
                                )}
                            </span>
                            {guidance[dose.entryId] && (
                                <div className={`dose-guidance is-${guidance[dose.entryId].action.toLowerCase()}`} role="note">
                                    <strong>Ką daryti dabar</strong>
                                    <p>{guidance[dose.entryId].message}</p>
                                    {guidance[dose.entryId].supplementRule && (
                                        <p className="dose-rule">Papildo nurodymas: {guidance[dose.entryId].supplementRule}</p>
                                    )}
                                </div>
                            )}
                        </li>
                    ))}
                </ul>
            )}

            <h2 className="dose-history-title">Vartojimo istorija</h2>
            <div className="dose-period" role="group" aria-label="Laikotarpis">
                {periods.map(option => (
                    <button key={option.value} type="button" className={period === option.value ? 'selected' : ''}
                            onClick={() => setPeriod(option.value)}>{option.label}</button>
                ))}
            </div>

            {historyError && <p className="dose-error" role="alert">{historyError}</p>}
            {!history && !historyError && schedule && <p role="status">Kraunama…</p>}
            {history && (
                <>
                    {history.notice && <p className="dose-notice" role="alert">{history.notice}</p>}
                    {history.planned === 0 ? (
                        <p className="dose-muted">Šiame laikotarpyje vartojimo duomenų dar nėra.</p>
                    ) : (
                        <>
                            <div className="dose-stats">
                                <div><strong>{history.taken}</strong><span>Išgerta</span></div>
                                <div><strong>{history.skipped}</strong><span>Praleista</span></div>
                                <div><strong>{history.unmarked}</strong><span>Nepažymėta</span></div>
                                <div><strong>{history.adherencePercent ?? 0} %</strong><span>Nuoseklumas</span></div>
                            </div>
                            <div className="dose-bar" aria-hidden="true">
                                <div style={{ width: `${history.adherencePercent ?? 0}%` }} />
                            </div>
                            <table className="dose-table">
                                <thead>
                                <tr><th>Papildas</th><th>Išgerta</th><th>Praleista</th><th>Nepažymėta</th><th>Nuoseklumas</th></tr>
                                </thead>
                                <tbody>
                                {history.entries.map(entry => (
                                    <tr key={entry.entryId}>
                                        <td>{entry.supplementName}</td>
                                        <td>{entry.taken}</td>
                                        <td>{entry.skipped}</td>
                                        <td>{entry.unmarked}</td>
                                        <td>{entry.adherencePercent === null ? '–' : `${entry.adherencePercent} %`}</td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                            <p className="dose-muted">
                                Nuoseklumas = išgertos dozės ÷ suplanuotos dozės. Šiandienos dozė skaičiuojama tik pažymėjus.
                            </p>
                        </>
                    )}
                </>
            )}
        </section>
    )
}