/** Shifts an ISO date (YYYY-MM-DD) by whole days without any time zone surprises. */
export function shiftDate(iso: string, days: number): string {
    const [year, month, day] = iso.split('-').map(Number)
    const shifted = new Date(Date.UTC(year, month - 1, day + days))
    return shifted.toISOString().slice(0, 10)
}