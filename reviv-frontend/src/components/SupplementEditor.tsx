import { useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { api } from '../api'
import type { Supplement, SupplementFormKind } from '../types'
import './SupplementEditor.css'

const FORMS: { value: SupplementFormKind; label: string }[] = [
    { value: 'CAPSULE', label: 'Kapsulė' },
    { value: 'TABLET', label: 'Tabletė' },
    { value: 'POWDER', label: 'Milteliai' },
    { value: 'LIQUID', label: 'Skystis' },
]

interface Props {
    supplement: Supplement | null
    allSupplements: Supplement[]
    onSaved: (message: string) => void
    onChanged: () => void
    onCancel: () => void
}

type FieldErrors = Record<string, string>

interface ServerError {
    message?: string
    fields?: FieldErrors
}

function parseError(err: unknown): { fields: FieldErrors; message: string } {
    if (axios.isAxiosError<ServerError>(err) && err.response?.data) {
        const data = err.response.data
        return { fields: data.fields ?? {}, message: data.message ?? 'Nepavyko įvykdyti veiksmo.' }
    }
    return { fields: {}, message: 'Nepavyko susisiekti su serveriu.' }
}

function toNumber(value: string): number | null {
    const normalized = value.trim().replace(',', '.')
    if (normalized === '') return null
    const parsed = Number(normalized)
    return Number.isNaN(parsed) ? null : parsed
}

function fieldError(errors: FieldErrors, key: string) {
    return errors[key] ? <span className="field-error">{errors[key]}</span> : null
}

/** Papildo kūrimo ir redagavimo forma su platintojais ir sąveikomis (US-10). */
export default function SupplementEditor({ supplement, allSupplements, onSaved, onChanged, onCancel }: Props) {
    const sectionRef = useRef<HTMLElement>(null)
    const [name, setName] = useState(supplement?.name ?? '')
    const [activeSubstance, setActiveSubstance] = useState(supplement?.activeSubstance ?? '')
    const [form, setForm] = useState<SupplementFormKind | ''>(supplement?.form ?? '')
    const [doseAmount, setDoseAmount] = useState(supplement ? String(supplement.doseAmount) : '')
    const [doseUnit, setDoseUnit] = useState(supplement?.doseUnit ?? '')
    const [maxDailyDose, setMaxDailyDose] = useState(supplement ? String(supplement.maxDailyDose) : '')
    const [missedDoseRule, setMissedDoseRule] = useState(supplement?.missedDoseRule ?? '')
    const [distributors, setDistributors] = useState<{ name: string; url: string }[]>(
        supplement?.distributors.map((d) => ({ name: d.name, url: d.url })) ?? [],
    )
    const [errors, setErrors] = useState<FieldErrors>({})
    const [general, setGeneral] = useState('')
    const [saving, setSaving] = useState(false)

    const [otherId, setOtherId] = useState('')
    const [interactionType, setInteractionType] = useState('')
    const [interactionRecommendation, setInteractionRecommendation] = useState('')
    const [interactionErrors, setInteractionErrors] = useState<FieldErrors>({})
    const [interactionMessage, setInteractionMessage] = useState('')

    const availableForInteraction = supplement
        ? allSupplements.filter(
            (s) =>
                s.id !== supplement.id &&
                !supplement.interactions.some((i) => i.otherSupplementId === s.id),
        )
        : []

    // Atidarius formą, paslenkama prie jos (jei ji žemiau ekrano)
    useEffect(() => {
        sectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }, [])

    function updateDistributor(index: number, patch: Partial<{ name: string; url: string }>) {
        setDistributors((current) => current.map((d, i) => (i === index ? { ...d, ...patch } : d)))
    }

    async function save() {
        setSaving(true)
        setErrors({})
        setGeneral('')
        const payload = {
            name,
            activeSubstance,
            form: form === '' ? null : form,
            doseAmount: toNumber(doseAmount),
            doseUnit,
            maxDailyDose: toNumber(maxDailyDose),
            missedDoseRule,
            distributors,
        }
        try {
            if (supplement) {
                await api.put(`/api/supplements/${supplement.id}`, payload)
            } else {
                await api.post('/api/supplements', payload)
            }
            onSaved(supplement ? 'Papildas atnaujintas.' : 'Papildas pridėtas.')
        } catch (err) {
            const parsed = parseError(err)
            setErrors(parsed.fields)
            setGeneral(
                Object.keys(parsed.fields).length > 0 ? 'Patikrinkite pažymėtus laukus.' : parsed.message,
            )
        } finally {
            setSaving(false)
        }
    }

    async function addInteraction() {
        if (!supplement) return
        setInteractionErrors({})
        setInteractionMessage('')
        try {
            await api.post(`/api/supplements/${supplement.id}/interactions`, {
                otherSupplementId: otherId === '' ? null : Number(otherId),
                type: interactionType,
                recommendation: interactionRecommendation,
            })
            setOtherId('')
            setInteractionType('')
            setInteractionRecommendation('')
            onChanged()
        } catch (err) {
            const parsed = parseError(err)
            setInteractionErrors(parsed.fields)
            if (Object.keys(parsed.fields).length === 0) setInteractionMessage(parsed.message)
        }
    }

    async function removeInteraction(interactionId: number) {
        if (!supplement) return
        setInteractionMessage('')
        try {
            await api.delete(`/api/supplements/${supplement.id}/interactions/${interactionId}`)
            onChanged()
        } catch {
            setInteractionMessage('Nepavyko pašalinti sąveikos.')
        }
    }

    return (
        <section className="supplement-editor">
            <h2>{supplement ? `Redaguoti: ${supplement.name}` : 'Naujas papildas'}</h2>

            <div className="editor-grid">
                <label className="field">
                    Pavadinimas
                    <input value={name} onChange={(e) => setName(e.target.value)} aria-invalid={!!errors.name} />
                    {fieldError(errors, 'name')}
                </label>

                <label className="field">
                    Veiklioji medžiaga
                    <input
                        value={activeSubstance}
                        onChange={(e) => setActiveSubstance(e.target.value)}
                        aria-invalid={!!errors.activeSubstance}
                    />
                    {fieldError(errors, 'activeSubstance')}
                </label>

                <label className="field">
                    Forma
                    <select
                        value={form}
                        onChange={(e) => setForm(e.target.value as SupplementFormKind | '')}
                        aria-invalid={!!errors.form}
                    >
                        <option value="">Pasirinkite…</option>
                        {FORMS.map((f) => (
                            <option key={f.value} value={f.value}>
                                {f.label}
                            </option>
                        ))}
                    </select>
                    {fieldError(errors, 'form')}
                </label>

                <label className="field">
                    Matavimo vienetas
                    <input
                        value={doseUnit}
                        onChange={(e) => setDoseUnit(e.target.value)}
                        placeholder="mg, mcg, ml…"
                        aria-invalid={!!errors.doseUnit}
                    />
                    {fieldError(errors, 'doseUnit')}
                </label>

                <label className="field">
                    Kiekis vienoje dozėje
                    <input
                        value={doseAmount}
                        onChange={(e) => setDoseAmount(e.target.value)}
                        inputMode="decimal"
                        aria-invalid={!!errors.doseAmount}
                    />
                    {fieldError(errors, 'doseAmount')}
                </label>

                <label className="field">
                    Maksimali saugi paros dozė
                    <input
                        value={maxDailyDose}
                        onChange={(e) => setMaxDailyDose(e.target.value)}
                        inputMode="decimal"
                        aria-invalid={!!errors.maxDailyDose}
                    />
                    {fieldError(errors, 'maxDailyDose')}
                </label>
            </div>

            <label className="field">
                Dozės praleidimo taisyklė (nebūtina)
                <textarea
                    rows={2}
                    value={missedDoseRule}
                    onChange={(e) => setMissedDoseRule(e.target.value)}
                    aria-invalid={!!errors.missedDoseRule}
                />
                {fieldError(errors, 'missedDoseRule')}
            </label>

            <h3>Platintojai</h3>
            {distributors.length === 0 && <p className="muted small">Platintojų nėra.</p>}
            {distributors.map((d, index) => (
                <div className="distributor-row" key={index}>
                    <label className="field">
                        Pavadinimas
                        <input
                            value={d.name}
                            onChange={(e) => updateDistributor(index, { name: e.target.value })}
                            aria-invalid={!!errors[`distributors[${index}].name`]}
                        />
                        {fieldError(errors, `distributors[${index}].name`)}
                    </label>
                    <label className="field">
                        Nuoroda į parduotuvę
                        <input
                            value={d.url}
                            onChange={(e) => updateDistributor(index, { url: e.target.value })}
                            placeholder="https://…"
                            aria-invalid={!!errors[`distributors[${index}].url`]}
                        />
                        {fieldError(errors, `distributors[${index}].url`)}
                    </label>
                    <button
                        type="button"
                        className="btn secondary small"
                        onClick={() => setDistributors((current) => current.filter((_, i) => i !== index))}
                    >
                        Šalinti
                    </button>
                </div>
            ))}
            <button
                type="button"
                className="btn secondary small"
                onClick={() => setDistributors((current) => [...current, { name: '', url: '' }])}
            >
                Pridėti platintoją
            </button>

            {general && <p className="error">{general}</p>}

            <div className="row editor-actions">
                <button type="button" className="btn secondary" onClick={onCancel}>
                    Atšaukti
                </button>
                <button type="button" className="btn" disabled={saving} onClick={() => void save()}>
                    {saving ? 'Saugoma…' : 'Išsaugoti'}
                </button>
            </div>

            {supplement && (
                <div className="interactions">
                    <h3>Sąveikos su kitais papildais</h3>
                    {supplement.interactions.length === 0 && <p className="muted small">Sąveikų nėra.</p>}
                    <ul className="interaction-list">
                        {supplement.interactions.map((i) => (
                            <li key={i.id}>
                                <div>
                                    <strong>{i.otherSupplementName}</strong> – {i.type}
                                    <div className="muted small">{i.recommendation}</div>
                                </div>
                                <button
                                    type="button"
                                    className="btn secondary small"
                                    onClick={() => void removeInteraction(i.id)}
                                >
                                    Šalinti
                                </button>
                            </li>
                        ))}
                    </ul>

                    <div className="interaction-form">
                        <label className="field">
                            Papildas
                            <select
                                value={otherId}
                                onChange={(e) => setOtherId(e.target.value)}
                                aria-invalid={!!interactionErrors.otherSupplementId}
                            >
                                <option value="">Pasirinkite…</option>
                                {availableForInteraction.map((s) => (
                                    <option key={s.id} value={s.id}>
                                        {s.name}
                                    </option>
                                ))}
                            </select>
                            {fieldError(interactionErrors, 'otherSupplementId')}
                        </label>
                        <label className="field">
                            Sąveikos tipas
                            <input
                                value={interactionType}
                                onChange={(e) => setInteractionType(e.target.value)}
                                placeholder="pvz., Mažina pasisavimą"
                                aria-invalid={!!interactionErrors.type}
                            />
                            {fieldError(interactionErrors, 'type')}
                        </label>
                        <label className="field">
                            Rekomendacija
                            <input
                                value={interactionRecommendation}
                                onChange={(e) => setInteractionRecommendation(e.target.value)}
                                placeholder="pvz., Vartoti skirtingu paros metu"
                                aria-invalid={!!interactionErrors.recommendation}
                            />
                            {fieldError(interactionErrors, 'recommendation')}
                        </label>
                        <button type="button" className="btn small" onClick={() => void addInteraction()}>
                            Pridėti sąveiką
                        </button>
                    </div>
                    {interactionMessage && <p className="error">{interactionMessage}</p>}
                </div>
            )}
        </section>
    )
}