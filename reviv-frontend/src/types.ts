export type Role = 'USER' | 'ADMIN'

export interface User {
    id: number
    email: string
    name: string
    role: Role
}

export interface GoogleProfile {
    email?: string
    name?: string
    picture?: string
}

export interface ProfileData {
    heightCm?: number | null
    weightKg?: number | null
    harmfulHabits?: string[]
    allergies?: string[]
    updatedAt?: string
}
export type SupplementFormKind = 'CAPSULE' | 'TABLET' | 'POWDER' | 'LIQUID'
export type SupplementStatus = 'ACTIVE' | 'INACTIVE'

export interface Distributor {
    id?: number
    name: string
    url: string
}

export interface SupplementInteraction {
    id: number
    otherSupplementId: number
    otherSupplementName: string
    type: string
    recommendation: string
}

export interface Supplement {
    id: number
    name: string
    activeSubstance: string
    form: SupplementFormKind
    doseAmount: number
    doseUnit: string
    maxDailyDose: number
    missedDoseRule: string | null
    status: SupplementStatus
    distributors: Distributor[]
    interactions: SupplementInteraction[]
}