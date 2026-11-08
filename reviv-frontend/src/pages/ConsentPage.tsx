import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

// Pakeiskite į tikrą kontaktinį adresą, kuriuo naudotojai gali kreiptis dėl savo duomenų
const Contact = 'reviv@example.com'

export default function ConsentPage() {
    const { profile, acceptConsent, logout } = useAuth()
    const navigate = useNavigate()
    const [checked, setChecked] = useState(false)
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const onAccept = async () => {
        try {
            setBusy(true)
            setError(null)
            await acceptConsent()
            navigate('/', { replace: true })
        } catch {
            setError('Nepavyko išsaugoti sutikimo. Bandykite dar kartą.')
        } finally {
            setBusy(false)
        }
    }

    const onDecline = () => {
        logout()
        navigate('/login', { replace: true })
    }

    return (
        <div className="center-page">
            <div className="card wide">
                <h1>Sutikimas tvarkyti sveikatos duomenis</h1>
                <p className="muted">
                    Sveiki, {profile?.name}. Prieš sukurdami paskyrą, susipažinkite su tuo,
                    kaip „Reviv“ tvarko jūsų duomenis.
                </p>

                <div className="consent-text">
                    <h3>Kokie duomenys tvarkomi</h3>
                    <ul>
                        <li>Google paskyros vardas ir el. paštas (prisijungimui).</li>
                        <li>
                            Profilio duomenys: ūgis, svoris, žalingi įpročiai ir alergijos.
                        </li>
                        <li>
                            Jūsų įkelti kraujo tyrimų PDF dokumentai ir iš jų ištraukti
                            rodikliai.
                        </li>
                        <li>
                            Papildų rekomendacijos, vartojimo planas, priminimai ir dozių
                            žymėjimai.
                        </li>
                    </ul>

                    <h3>Kam jie naudojami</h3>
                    <p>
                        Rodikliams ištraukti iš tyrimų, jų būsenai ir paaiškinimams
                        pateikti, papildų rekomendacijoms ir vartojimo planui sudaryti,
                        priminimams siųsti bei suvestinei gydytojui sugeneruoti.
                    </p>

                    <h3>Dirbtinis intelektas</h3>
                    <p>
                        Rodikliams iš PDF ištraukti naudojamas vietinis DI modelis, veikiantis
                        sistemos kompiuteryje. Paaiškinimams ir papildų pasiūlymams rengti
                        duomenys siunčiami į išorinę paslaugą (OpenAI API), tačiau prieš tai
                        pašalinami visi jūsų identifikatoriai: vardas, el. paštas ir
                        paskyros numeris. DI sugeneruotas turinys žymimas „Sugeneruota DI“.
                    </p>

                    <h3>Svarbu žinoti</h3>
                    <p>
                        „Reviv“ nėra medicinos priemonė ir neteikia diagnozių ar gydymo
                        rekomendacijų. Rezultatus ir papildų vartojimą aptarkite su
                        gydytoju. Jei rodiklis viršija kritinę ribą, sistema papildų
                        nesiūlo ir rekomenduoja kreiptis į gydytoją.
                    </p>

                    <h3>Jūsų teisės</h3>
                    <p>
                        Sutikimą galite bet kada atšaukti. Turite teisę susipažinti su savo
                        duomenimis, juos ištaisyti ar ištrinti: tyrimus galite ištrinti
                        patys, o kitus duomenis – kreipdamiesi el. paštu{' '}
                        <strong>{Contact}</strong>. Sutikimo data išsaugoma. Skundą galite
                        pateikti Valstybinei duomenų apsaugos inspekcijai (vdai.lrv.lt).
                    </p>
                </div>

                <label className="checkbox">
                    <input
                        type="checkbox"
                        checked={checked}
                        onChange={(e) => setChecked(e.target.checked)}
                    />
                    Susipažinau ir aiškiai sutinku, kad būtų tvarkomi mano sveikatos
                    duomenys aprašytais tikslais
                </label>

                {error && <p className="error">{error}</p>}

                <div className="row">
                    <button className="btn secondary" onClick={onDecline} disabled={busy}>
                        Nesutinku (atsijungti)
                    </button>
                    <button
                        className="btn"
                        onClick={onAccept}
                        disabled={!checked || busy}
                    >
                        {busy ? 'Saugoma…' : 'Sutinku'}
                    </button>
                </div>
            </div>
        </div>
    )
}