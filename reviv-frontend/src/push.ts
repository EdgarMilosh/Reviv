import { api } from './api'

export type PushStatus = 'unsupported' | 'denied' | 'disabled' | 'enabled'

export interface TestPushResult {
    subscriptions: number
    sent: number
    failed: number
}

/** Web Push needs a service worker, the Push API and notifications (secure context: https or localhost). */
export function pushSupported(): boolean {
    return typeof window !== 'undefined'
        && 'serviceWorker' in navigator
        && 'PushManager' in window
        && 'Notification' in window
}

function urlBase64ToUint8Array(base64: string) {
    const padding = '='.repeat((4 - (base64.length % 4)) % 4)
    const normalized = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/')
    const raw = atob(normalized)
    const output = new Uint8Array(raw.length)
    for (let i = 0; i < raw.length; i += 1) {
        output[i] = raw.charCodeAt(i)
    }
    return output
}

export async function currentPushStatus(): Promise<PushStatus> {
    if (!pushSupported()) return 'unsupported'
    if (Notification.permission === 'denied') return 'denied'
    const registration = await navigator.serviceWorker.getRegistration()
    const subscription = await registration?.pushManager.getSubscription()
    return subscription ? 'enabled' : 'disabled'
}

/** Asks for permission, subscribes this browser and stores the subscription on the server. */
export async function enablePush(): Promise<void> {
    const { data } = await api.get<{ publicKey: string }>('/api/push/public-key')
    if (!data.publicKey) throw new Error('NO_KEY')

    const permission = await Notification.requestPermission()
    if (permission !== 'granted') throw new Error('PERMISSION_DENIED')

    await navigator.serviceWorker.register('/sw.js')
    const registration = await navigator.serviceWorker.ready
    const subscription = (await registration.pushManager.getSubscription())
        ?? (await registration.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: urlBase64ToUint8Array(data.publicKey),
        }))

    const json = subscription.toJSON()
    await api.post('/api/push/subscriptions', {
        endpoint: json.endpoint,
        keys: { p256dh: json.keys?.p256dh, auth: json.keys?.auth },
    })
}

export async function disablePush(): Promise<void> {
    const registration = await navigator.serviceWorker.getRegistration()
    const subscription = await registration?.pushManager.getSubscription()
    if (!subscription) return
    await api.delete('/api/push/subscriptions', { params: { endpoint: subscription.endpoint } })
    await subscription.unsubscribe()
}

export async function sendTestPush(): Promise<TestPushResult> {
    const { data } = await api.post<TestPushResult>('/api/push/test')
    return data
}