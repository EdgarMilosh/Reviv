/* Reviv service worker: shows push reminders and opens the app when a notification is clicked. */

self.addEventListener('install', () => {
    self.skipWaiting()
})

self.addEventListener('activate', (event) => {
    event.waitUntil(self.clients.claim())
})

self.addEventListener('push', (event) => {
    let data = {}
    if (event.data) {
        try {
            data = event.data.json()
        } catch {
            data = { body: event.data.text() }
        }
    }
    const title = data.title || 'Reviv'
    const options = {
        body: data.body || '',
        icon: '/favicon.svg',
        badge: '/favicon.svg',
        data: { url: data.url || '/' },
    }
    event.waitUntil(self.registration.showNotification(title, options))
})

self.addEventListener('notificationclick', (event) => {
    event.notification.close()
    const target = new URL((event.notification.data && event.notification.data.url) || '/', self.location.origin).href

    event.waitUntil(
        (async () => {
            const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
            for (const client of windows) {
                if (new URL(client.url).origin === self.location.origin) {
                    try {
                        await client.focus()
                        if ('navigate' in client) {
                            await client.navigate(target)
                            return
                        }
                    } catch {
                        // fall through and open a new window
                    }
                }
            }
            await self.clients.openWindow(target)
        })(),
    )
})