import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import axios from 'axios'
import { api } from '../api'
import ConsumptionPlan, { type ConsumptionPlanData } from '../components/ConsumptionPlan'
import './PlansPage.css'

export default function PlanDetailPage() {
    const { planId } = useParams<{ planId: string }>()
    const navigate = useNavigate()
    const id = Number(planId)
    const validId = Number.isSafeInteger(id) && id > 0
    const [plan, setPlan] = useState<ConsumptionPlanData | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')

    useEffect(() => {
        if (!validId) return
        let cancelled = false
        api.get<ConsumptionPlanData>(`/api/planai/${id}`)
            .then(response => { if (!cancelled) { setPlan(response.data); setError('') } })
            .catch(err => {
                if (!cancelled) {
                    const data = axios.isAxiosError(err) ? err.response?.data as { detail?: string; message?: string } | undefined : undefined
                    setError(data?.detail ?? data?.message ?? 'Nepavyko atidaryti vartojimo plano.')
                }
            })
            .finally(() => { if (!cancelled) setLoading(false) })
        return () => { cancelled = true }
    }, [id, validId])

    if (!validId) return <div className="plans-message plans-error"><p>Neteisingas vartojimo plano numeris.</p><Link to="/plans">Grįžti į planų sąrašą</Link></div>
    if (loading) return <p role="status">Įkeliamas vartojimo planas...</p>
    if (error) return <div className="plans-message plans-error" role="alert"><p>{error}</p><Link to="/plans">Grįžti į planų sąrašą</Link></div>
    if (!plan) return null

    return <ConsumptionPlan key={plan.id} data={plan} onChange={setPlan} onBack={() => navigate('/plans')}
        backLabel="Grįžti į mano planus" direct />
}
