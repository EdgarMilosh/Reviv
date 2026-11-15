import { useEffect, useState } from 'react'
import axios from 'axios'
import { currentPushStatus, disablePush, enablePush, sendTestPush, type PushStatus } from '../push'
import './ReminderSettings.css'

function describeError(err: unknown): string {
    if (err instanceof Error && err.message === 'PERMISSION_DENIED') {
        return 'Naršyklė neleido rodyti pranešimų. Leiskite juos naršyklės svetainės nustatymuose.'
    }
    if (err instanceof Error && err.message === 'NO_KEY') {
        return 'Serveryje nenustatyti VAPID raktai (VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY).'
    }
    if (axios.isAxiosError(err)) {
        return 'Nepavyko susisiekti su serveriu.'
    }
    return 'Nepavyko įjungti priminimų šioje naršyklėje.'
}

/** US-12: lets the user turn push reminders on or off in this browser and send a test notification. */
export default function ReminderSettings() {
    const [status, setStatus] = useState<PushStatus | 'loading'>('loading')
    const [busy, setBusy] = useState(false)
    const [message, setMessage] = useState('')
    const [error, setError] = useState('')

    useEffect(() => {
        let active = true
        currentPushStatus()
            .then((value) => {
                if (active) setStatus(value)
            })
            .catch(() => {
                if (active) setStatus('disabled')
            })
        return () => {
            active = false
        }
    }, [])

    async function run(action: () => Promise<void>) {
        setBusy(true)
        setMessage('')
        setError('')
        try {
            await action()
        } catch (err) {
            setError(describeError(err))
        } finally {
            setStatus(await currentPushStatus().catch(() => 'disabled' as const))
            setBusy(false)
        }
    }

    function enable() {
        return run(async () => {
            await enablePush()
            setMessage('Priminimai įjungti šioje naršyklėje.')
        })
    }

    function disable() {
        return run(async () => {
            await disablePush()
            setMessage('Priminimai šioje naršyklėje išjungti.')
        })
    }

    function test() {
        return run(async () => {
            const result = await sendTestPush()
            if (result.sent > 0) {
                setMessage('Bandomasis pranešimas išsiųstas. Netrukus jį turėtumėte pamatyti.')
            } else if (result.subscriptions === 0) {
                setError('Serveris nežino jūsų įrenginio. Išjunkite ir vėl įjunkite priminimus.')
            } else {
                setError('Pranešimo išsiųsti nepavyko. Patikrinkite serverio žurnalą ir VAPID raktus.')
            }
        })
    }

    return (
        <section className="reminder-settings" aria-label="Priminimai">
            <div>
                <h2>Priminimai</h2>
                <p>
                    {status === 'enabled' && 'Pranešimai apie vartojimo laiką bus rodomi šioje naršyklėje.'}
                    {status === 'disabled' && 'Įjunkite pranešimus, kad gautumėte priminimą, kada išgerti papildus.'}
                    {status === 'denied' && 'Naršyklė blokuoja pranešimus. Leiskite juos svetainės nustatymuose ir perkraukite puslapį.'}
                    {status === 'unsupported' && 'Ši naršyklė nepalaiko pranešimų. Telefone pridėkite svetainę prie pradžios ekrano.'}
                    {status === 'loading' && 'Tikrinama…'}
                </p>
            </div>
            <div className="reminder-actions">
                {status === 'disabled' && (
                    <button type="button" className="btn" disabled={busy} onClick={() => void enable()}>
                        Įjungti priminimus
                    </button>
                )}
                {status === 'enabled' && (
                    <>
                        <button type="button" className="btn secondary" disabled={busy} onClick={() => void test()}>
                            Siųsti bandomąjį pranešimą
                        </button>
                        <button type="button" className="btn secondary" disabled={busy} onClick={() => void disable()}>
                            Išjungti
                        </button>
                    </>
                )}
            </div>
            {message && <p className="reminder-message" role="status">{message}</p>}
            {error && <p className="reminder-error" role="alert">{error}</p>}
        </section>
    )
}