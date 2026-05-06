const API_BASE = '/api'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!response.ok) {
    throw new Error(`API Error: ${response.status} ${response.statusText}`)
  }
  if (response.status === 204) return null as T
  return response.json()
}

export function get<T>(path: string): Promise<T> {
  return request<T>(path)
}

export function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined })
}

export function put<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, { method: 'PUT', body: body ? JSON.stringify(body) : undefined })
}

export function del<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'DELETE' })
}

export async function uploadFile<T>(path: string, file: File, fieldName: string = 'file'): Promise<T> {
  const formData = new FormData()
  formData.append(fieldName, file)
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    body: formData,
  })
  if (!response.ok) {
    const errorBody = await response.text()
    throw new Error(`API Error: ${response.status} ${response.statusText}: ${errorBody}`)
  }
  if (response.status === 204) return null as T
  return response.json()
}

export async function uploadFilePut<T>(path: string, file: File, fieldName: string = 'file'): Promise<T> {
  const formData = new FormData()
  formData.append(fieldName, file)
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'PUT',
    body: formData,
  })
  if (!response.ok) {
    const errorBody = await response.text()
    throw new Error(`API Error: ${response.status} ${response.statusText}: ${errorBody}`)
  }
  if (response.status === 204) return null as T
  return response.json()
}

export function sseStream(path: string, onEvent: (data: unknown) => void): EventSource {
  const source = new EventSource(`${API_BASE}${path}`)
  source.onmessage = (event) => {
    try {
      onEvent(JSON.parse(event.data))
    } catch {
      onEvent(event.data)
    }
  }
  return source
}

export function websocketUrl(path: string): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}${path}`
}
