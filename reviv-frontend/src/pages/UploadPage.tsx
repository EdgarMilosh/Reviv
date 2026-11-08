import { useRef, useState, type DragEvent } from 'react'
import axios from 'axios'
import { api } from '../api'
import './UploadPage.css'

type UploadResponse = {
    id: number
    fileName: string
    fileSize: number
    status: string
    uploadedAt: string
}

const MAX_SIZE = 10 * 1024 * 1024

export default function UploadPage() {
    const inputRef = useRef<HTMLInputElement>(null)

    const [file, setFile] = useState<File | null>(null)
    const [loading, setLoading] = useState(false)
    const [result, setResult] = useState<UploadResponse | null>(null)
    const [error, setError] = useState('')

    function selectFile(selected: File | null) {
        setError('')
        setResult(null)
        setFile(null)

        if (!selected) return

        if (!selected.name.toLowerCase().endsWith('.pdf')) {
            setError('Galima įkelti tik PDF dokumentus.')
            return
        }

        if (selected.size > MAX_SIZE) {
            setError('PDF failas negali viršyti 10 MB.')
            return
        }

        setFile(selected)
    }

    function handleDrop(event: DragEvent<HTMLDivElement>) {
        event.preventDefault()
        selectFile(event.dataTransfer.files[0] ?? null)
    }

    async function upload(allowDuplicate = false) {
        if (!file || loading) return

        setLoading(true)
        setError('')

        const data = new FormData()
        data.append('file', file)

        try {
            const response = await api.post<UploadResponse>(
                '/api/tyrimai',
                data,
                { params: { allowDuplicate } }
            )

            setResult(response.data)
        } catch (err) {
            if (axios.isAxiosError(err)) {
                if (err.response?.status === 409) {
                    const confirmed = window.confirm(
                        'Šis dokumentas jau buvo įkeltas. ' +
                        'Ar norite įkelti jį dar kartą?'
                    )

                    if (confirmed) {
                        setLoading(false)
                        await upload(true)
                        return
                    }

                    setError('Pakartotinis įkėlimas atšauktas.')
                } else {
                    const data = err.response?.data
                    setError(
                        data?.detail ||
                        data?.message ||
                        'Nepavyko įkelti dokumento.'
                    )
                }
            } else {
                setError('Nepavyko susisiekti su serveriu.')
            }
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="upload-page">
            <h1>Įkelti tyrimą</h1>

            <div
                className="upload-dropzone"
                onDragOver={(e) => e.preventDefault()}
                onDrop={handleDrop}
            >
                <strong>Nutempkite PDF failą čia</strong>

                <p>arba</p>

                <input
                    ref={inputRef}
                    type="file"
                    accept=".pdf,application/pdf"
                    hidden
                    onChange={(e) =>
                        selectFile(e.target.files?.[0] ?? null)
                    }
                />

                <button
                    type="button"
                    onClick={() => inputRef.current?.click()}
                    disabled={loading}
                >
                    Pasirinkti failą
                </button>

                <p>Tik PDF, iki 10 MB</p>
            </div>

            {file && (
                <div className="upload-file">
                    <span>
                        {file.name} ·
                        {' '}
                        {(file.size / 1024 / 1024).toFixed(2)} MB
                    </span>

                    {result ? (
                        <strong className="upload-success">
                            Įkelta
                        </strong>
                    ) : (
                        <button
                            type="button"
                            onClick={() => upload()}
                            disabled={loading}
                        >
                            {loading ? 'Įkeliama...' : 'Įkelti'}
                        </button>
                    )}
                </div>
            )}

            {error && (
                <p className="upload-error" role="alert">
                    {error}
                </p>
            )}
        </div>
    )
}
