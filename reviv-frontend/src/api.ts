import axios from 'axios'

export const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL,
})

// Prie kiekvienos užklausos prideda Google ID tokeną
api.interceptors.request.use((config) => {
    const token = localStorage.getItem('token')
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})