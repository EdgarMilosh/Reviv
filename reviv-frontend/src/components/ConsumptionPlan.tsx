import { useState } from 'react'
import axios from 'axios'
import { api } from '../api'
import './ConsumptionPlan.css'

export type DayPart = 'MORNING' | 'NOON' | 'EVENING'
export type MealRelation = 'BEFORE_MEAL' | 'WITH_MEAL' | 'AFTER_MEAL' | 'INDEPENDENT' | 'UNKNOWN'

export type PlanEntry = {
    id: number
    recommendationId: number
    supplementId: number
    supplementName: string
    dailyDose: number
    doseUnit: string
    catalogMaxDailyDose: number | null
    dayPart: DayPart
    scheduledTime: string
    mealRelation: MealRelation
    startDate: string
    endDate: string
    breakDays: number | null
    notes: string
    reminderTime: string | null
    warnings: string[]
}

export type ConsumptionPlanData = {
    id: number
    bloodTestId: number
    generationId: number
    status: 'DRAFT' | 'ACTIVE'
    aiModel: string
    medicallyVerified: boolean
    entries: PlanEntry[]
}

type Editable = Pick<PlanEntry, 'dayPart' | 'scheduledTime' | 'mealRelation' | 'startDate' | 'endDate' | 'breakDays'>

const slots: { value: DayPart; label: string; time: string; times: string[] }[] = [
    { value: 'MORNING', label: 'Rytas', time: '08:00', times: ['07:00', '08:00', '09:00'] },
    { value: 'NOON', label: 'Pietūs', time: '13:00', times: ['12:00', '13:00', '14:00'] },
    { value: 'EVENING', label: 'Vakaras', time: '19:00', times: ['18:00', '19:00', '20:00'] },
]

const mealLabels: Record<MealRelation, string> = {
    BEFORE_MEAL: 'Prieš valgį',
    WITH_MEAL: 'Su maistu',
    AFTER_MEAL: 'Po valgio',
    INDEPENDENT: 'Nesiejama su maistu',
    UNKNOWN: 'Reikia patikslinti',
}

function errorMessage(error: unknown) {
    if (axios.isAxiosError(error)) {
        const message = (error.response?.data as { detail?: string; message?: string } | undefined)
        return message?.detail ?? message?.message ?? 'Nepavyko išsaugoti pakeitimų.'
    }
    return 'Nepavyko susisiekti su serveriu.'
}

function makeEditable(entry: PlanEntry): Editable {
    return {
        dayPart: entry.dayPart,
        scheduledTime: entry.scheduledTime.slice(0, 5),
        mealRelation: entry.mealRelation,
        startDate: entry.startDate,
        endDate: entry.endDate,
        breakDays: entry.breakDays,
    }
}

export default function ConsumptionPlan({ data, onBack, onChange, direct = false, backLabel = 'Grįžti prie rekomendacijų' }: {
    data: ConsumptionPlanData
    onBack: () => void
    onChange: (value: ConsumptionPlanData) => void
    direct?: boolean
    backLabel?: string
}) {
    const [edits, setEdits] = useState<Record<number, Editable>>(() =>
        Object.fromEntries(data.entries.map(entry => [entry.id, makeEditable(entry)]))
    )
    const [busyId, setBusyId] = useState<number | null>(null)
    const [activating, setActivating] = useState(false)
    const [error, setError] = useState('')
    const [success, setSuccess] = useState('')

    function change(id: number, partial: Partial<Editable>) {
        setEdits(previous => ({ ...previous, [id]: { ...previous[id], ...partial } }))
        setSuccess('')
    }

    async function save(entry: PlanEntry) {
        const edit = edits[entry.id]
        if (!edit || busyId !== null || activating) return
        setBusyId(entry.id)
        setError('')
        setSuccess('')
        try {
            const response = await api.patch<ConsumptionPlanData>(
                direct ? `/api/planai/${data.id}/entries/${entry.id}` : `/api/tyrimai/${data.bloodTestId}/plan/entries/${entry.id}`,
                edit
            )
            onChange(response.data)
            setEdits(Object.fromEntries(response.data.entries.map(item => [item.id, makeEditable(item)])))
            setSuccess('Plano įrašas išsaugotas. Priminimų laikas atnaujintas, jei planas aktyvus.')
        } catch (err) {
            setError(errorMessage(err))
        } finally {
            setBusyId(null)
        }
    }

    async function activate() {
        if (busyId !== null || activating) return
        const hasUnsaved = data.entries.some(entry => {
            const edited = edits[entry.id]
            return edited != null && JSON.stringify(edited) !== JSON.stringify(makeEditable(entry))
        })
        if (hasUnsaved) {
            setError('Pirmiausia išsaugokite visų pakeistų papildų nustatymus.')
            return
        }
        setActivating(true)
        setError('')
        setSuccess('')
        try {
            const response = await api.post<ConsumptionPlanData>(
                direct ? `/api/planai/${data.id}/activate` : `/api/tyrimai/${data.bloodTestId}/plan/activate`
            )
            onChange(response.data)
            setEdits(Object.fromEntries(response.data.entries.map(item => [item.id, makeEditable(item)])))
            setSuccess('Planas sėkmingai išsaugotas.')
        } catch (err) {
            setError(errorMessage(err))
        } finally {
            setActivating(false)
        }
    }

    const unsavedChanges = data.entries.some(entry => {
        const edited = edits[entry.id]
        return edited != null && JSON.stringify(edited) !== JSON.stringify(makeEditable(entry))
    })

    const groups = slots.map(slot => ({
        ...slot,
        entries: data.entries.filter(entry => entry.dayPart === slot.value),
    }))

    return (
        <section className="plan-page">
            <button type="button" className="plan-back" onClick={onBack}>← {backLabel}</button>
            <div className="plan-heading">
                <div>
                    <h2>Papildų vartojimo planas</h2>
                    <p>
                        Papildų vartojimo tvarkaraštis ·
                        {data.status === 'ACTIVE' ? 'Aktyvus planas' : 'Juodraštis'}
                    </p>
                </div>
                <span className="plan-ai">Sudaryta DI · {data.aiModel}</span>
            </div>
            <div className="plan-disclaimer" role="note">
                Šis tvarkaraštis yra informacinis, o dozės ir sąveikos nėra mediciniškai patvirtintos.
                Vien papildų atskyrimas paros metu negarantuoja saugaus vartojimo.
                Prieš vartojimą dozes, ciklus ir sąveikas reikėtų patikrinti su specialistu.
                Katalogo maksimali dozė nėra individualiai saugios dozės patvirtinimas.
            </div>
            <h3 className="plan-section-title">Dienos tvarkaraštis</h3>
            <div className="plan-timeline">
                {groups.map(group => (
                    <div className="plan-slot" key={group.value}>
                        <div className="plan-slot-label"><strong>{group.label}</strong><small>{group.times[0]}–{group.times[2]}</small></div>
                        <div className="plan-slot-items">
                            {group.entries.length === 0 && <span className="plan-muted">Nesuplanuota</span>}
                            {group.entries.map(entry => (
                                <p key={entry.id}><strong>{entry.scheduledTime.slice(0, 5)}</strong> · <strong>{entry.supplementName}</strong> · {entry.dailyDose} {entry.doseUnit} · {mealLabels[entry.mealRelation]}</p>
                            ))}
                        </div>
                    </div>
                ))}
            </div>
            <h3 className="plan-section-title">Redaguoti planą ir ciklus</h3>
            <div className="plan-entries">
                {data.entries.map(entry => {
                    const edit = edits[entry.id]
                    if (!edit) return null
                    return (
                        <article key={entry.id} className="plan-entry">
                            <div className="plan-entry-heading">
                                <h4>{entry.supplementName}</h4>
                                <span>{entry.doseUnit} per parą</span>
                            </div>
                            <div className="plan-form">
                                <div className="plan-dose-field">
                                    <span>Paros dozė ({entry.doseUnit})</span>
                                    <output className="plan-dose-readonly" aria-label="Patvirtinta paros dozė">{entry.dailyDose} {entry.doseUnit}</output>
                                </div>
                                <label>Paros laikas
                                    <select value={edit.dayPart} onChange={e => {
                                        const dayPart = e.target.value as DayPart
                                        const standardTime = slots.find(slot => slot.value === dayPart)?.time ?? '08:00'
                                        change(entry.id, { dayPart, scheduledTime: standardTime })
                                    }}>
                                        {slots.map(slot => <option key={slot.value} value={slot.value}>{slot.label}</option>)}
                                    </select>
                                </label>
                                <label>Vartojimo valanda (±1 val.)
                                    <select value={edit.scheduledTime} onChange={e => change(entry.id, { scheduledTime: e.target.value })}>
                                        {slots.find(slot => slot.value === edit.dayPart)?.times.map(time =>
                                            <option key={time} value={time}>{time}</option>)}
                                    </select>
                                </label>
                                <label>Santykis su maistu
                                    <select value={edit.mealRelation} onChange={e => change(entry.id, { mealRelation: e.target.value as MealRelation })}>
                                        {Object.entries(mealLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                                    </select>
                                </label>
                                <label>Ciklo pradžia
                                    <input type="date" value={edit.startDate} onChange={e => change(entry.id, { startDate: e.target.value })} />
                                </label>
                                <label>Ciklo pabaiga
                                    <input type="date" value={edit.endDate} onChange={e => change(entry.id, { endDate: e.target.value })} />
                                </label>
                                <label>Pertrauka iki kito ciklo (dienos)
                                    <input type="number" min="0" max="365" placeholder="Nenurodyta"
                                        value={edit.breakDays ?? ''}
                                        onChange={e => change(entry.id, { breakDays: e.target.value === '' ? null : Number(e.target.value) })} />
                                </label>
                            </div>
                            {entry.notes && <p className="plan-notes"><strong>DI pastaba:</strong> {entry.notes}</p>}
                            {entry.warnings.length > 0 && (
                                <ul className="plan-warnings">
                                    {entry.warnings.map((warning, index) => <li key={`${index}-${warning}`}>{warning}</li>)}
                                </ul>
                            )}
                            {entry.reminderTime && <p className="plan-reminder">Priminimo įrašas: {entry.reminderTime.slice(0, 5)}</p>}
                            <button type="button" className="plan-save" disabled={busyId !== null || activating}
                                onClick={() => void save(entry)}>
                                {busyId === entry.id ? 'Saugoma...' : 'Išsaugoti pakeitimus'}
                            </button>
                        </article>
                    )
                })}
            </div>
            {error && <p role="alert" className="plan-error">{error}</p>}
            {success && <p role="status" className="plan-success">{success}</p>}
            <div className="plan-footer">
                {unsavedChanges && <p className="plan-unsaved">Yra neišsaugotų pakeitimų – išsaugokite kiekvieną pakeistą įrašą.</p>}
                {data.status === 'DRAFT' ? (
                    <button type="button" className="plan-activate" onClick={() => void activate()}
                        disabled={busyId !== null || activating || unsavedChanges}>
                        {activating ? 'Išsaugoma...' : 'Patvirtinti ir išsaugoti planą'}
                    </button>
                ) : <span className="plan-active">Planas išsaugotas · pakeitimus galima redaguoti</span>}
            </div>
        </section>
    )
}
