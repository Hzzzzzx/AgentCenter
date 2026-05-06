import { sseStream } from './client'
import type { RuntimeEventDto } from './types'

export const eventApi = {
  streamSessionEvents: (sessionId: string, onEvent: (event: RuntimeEventDto) => void) =>
    sseStream(`/agent-sessions/${sessionId}/events`, onEvent as (data: unknown) => void),
}
