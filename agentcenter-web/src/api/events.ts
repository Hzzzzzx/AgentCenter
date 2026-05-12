import { sseStream } from './client'
import type { RuntimeEventDto } from './types'

export const eventApi = {
  streamSessionEvents: (
    sessionId: string,
    onEvent: (event: RuntimeEventDto) => void,
    onError?: (event: Event) => void,
    options?: { afterSeq?: number | null; limit?: number | null },
  ) =>
    sseStream(sessionEventsPath(sessionId, options), onEvent as (data: unknown) => void, onError),
}

function sessionEventsPath(sessionId: string, options?: { afterSeq?: number | null; limit?: number | null }): string {
  const params = new URLSearchParams()
  if (typeof options?.afterSeq === 'number') {
    params.set('afterSeq', String(options.afterSeq))
  }
  if (typeof options?.limit === 'number') {
    params.set('limit', String(options.limit))
  }
  const query = params.toString()
  return `/agent-sessions/${sessionId}/events${query ? `?${query}` : ''}`
}
