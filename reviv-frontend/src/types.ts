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