import { describe, it, expect, vi, beforeEach } from 'vitest'
import { get, post, put } from '../api/client'

describe('API client', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('get() returns parsed JSON on success', async () => {
    const data = [{ id: '1', name: 'test' }]
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(data),
    } as Response)

    const result = await get<{ id: string; name: string }[]>('/test')
    expect(result).toEqual(data)
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/test',
      expect.objectContaining({ headers: { 'Content-Type': 'application/json' } }),
    )
  })

  it('post() sends JSON body and returns parsed response', async () => {
    const responseBody = { id: '2', title: 'created' }
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 201,
      json: () => Promise.resolve(responseBody),
    } as Response)

    const result = await post<{ id: string; title: string }>('/test', { title: 'created' })
    expect(result).toEqual(responseBody)
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/test',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ title: 'created' }),
      }),
    )
  })

  it('put() sends JSON body with PUT method', async () => {
    const responseBody = { id: '3', title: 'updated' }
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(responseBody),
    } as Response)

    const result = await put<{ id: string; title: string }>('/test/3', { title: 'updated' })
    expect(result).toEqual(responseBody)
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/test/3',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ title: 'updated' }),
      }),
    )
  })

  it('throws on non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 404,
      statusText: 'Not Found',
    } as Response)

    await expect(get('/test/missing')).rejects.toThrow('API Error: 404 Not Found')
  })

  it('returns null for 204 No Content', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 204,
    } as Response)

    const result = await get<void>('/test/no-content')
    expect(result).toBeNull()
  })

  it('post() without body sends undefined body', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    } as Response)

    await post('/test/action')
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/test/action',
      expect.objectContaining({ method: 'POST', body: undefined }),
    )
  })
})
